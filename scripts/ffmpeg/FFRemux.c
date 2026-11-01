#include "FFRemux.h"

#include <libavcodec/avcodec.h>
#include <libavformat/avformat.h>
#include <libavutil/audio_fifo.h>
#include <libavutil/channel_layout.h>
#include <libavutil/opt.h>
#include <libavutil/samplefmt.h>
#include <libswresample/swresample.h>
#include <stdio.h>
#include <string.h>

static int av1_playable = 0;

void ff_set_av1_playable(int playable) {
    av1_playable = playable;
}

int ff_can_play_video(const char *codec_name) {
    if (strcmp(codec_name, "av1") == 0) return av1_playable;
    return strcmp(codec_name, "h264") == 0 || strcmp(codec_name, "hevc") == 0;
}

static int audio_is_playable(enum AVCodecID id) {
    switch (id) {
    case AV_CODEC_ID_AAC:
    case AV_CODEC_ID_AC3:
    case AV_CODEC_ID_EAC3:
    case AV_CODEC_ID_ALAC:
    case AV_CODEC_ID_MP3:
        return 1;
    default:
        return 0;
    }
}

/// One input stream and what becomes of it.
typedef struct {
    int out_index;            // index in the output, or -1 when left out
    int transcode;            // audio decoded and written as AAC
    AVCodecContext *decoder;
    AVCodecContext *encoder;
    SwrContext *resampler;
    AVAudioFifo *fifo;
    AVFrame *decoded;
    AVFrame *converted;
    int64_t next_pts;         // in the encoder's time base (1/sample rate)
    int started;
} StreamPlan;

static void set_message(char *message, size_t size, const char *text, int error) {
    if (!message || !size) return;
    char detail[AV_ERROR_MAX_STRING_SIZE] = "";
    if (error < 0) av_strerror(error, detail, sizeof detail);
    snprintf(message, size, "%s%s%s", text, error < 0 ? ": " : "", detail);
}

static int container_is_playable(const AVFormatContext *input) {
    return strstr(input->iformat->name, "mp4") != NULL;
}

static void free_plan(StreamPlan *plan, unsigned count) {
    for (unsigned i = 0; i < count; i++) {
        avcodec_free_context(&plan[i].decoder);
        avcodec_free_context(&plan[i].encoder);
        swr_free(&plan[i].resampler);
        if (plan[i].fifo) av_audio_fifo_free(plan[i].fifo);
        av_frame_free(&plan[i].decoded);
        av_frame_free(&plan[i].converted);
    }
    av_free(plan);
}

/// Sets up the decode, convert and encode chain for one audio stream; the encoder's parameters go to `out`.
static int open_audio_chain(StreamPlan *plan, AVStream *in, AVStream *out, AVFormatContext *output) {
    const AVCodec *decoder = avcodec_find_decoder(in->codecpar->codec_id);
    const AVCodec *encoder = avcodec_find_encoder(AV_CODEC_ID_AAC);
    if (!decoder || !encoder) return AVERROR_DECODER_NOT_FOUND;

    plan->decoder = avcodec_alloc_context3(decoder);
    plan->encoder = avcodec_alloc_context3(encoder);
    if (!plan->decoder || !plan->encoder) return AVERROR(ENOMEM);
    int error = avcodec_parameters_to_context(plan->decoder, in->codecpar);
    if (error < 0) return error;
    plan->decoder->pkt_timebase = in->time_base;
    error = avcodec_open2(plan->decoder, decoder, NULL);
    if (error < 0) return error;

    // The AAC encoder takes mono up to 7.1; anything else is mixed down to what it can write.
    AVChannelLayout layout;
    av_channel_layout_copy(&layout, &plan->decoder->ch_layout);
    int channels = layout.nb_channels;
    if (layout.order == AV_CHANNEL_ORDER_UNSPEC || channels > 8 || channels == 0) {
        av_channel_layout_uninit(&layout);
        av_channel_layout_default(&layout, channels > 0 && channels <= 8 ? channels : 2);
        channels = layout.nb_channels;
    }
    int sample_rate = plan->decoder->sample_rate > 0 ? plan->decoder->sample_rate : 48000;

    plan->encoder->sample_fmt = AV_SAMPLE_FMT_FLTP;
    plan->encoder->sample_rate = sample_rate;
    plan->encoder->time_base = (AVRational){1, sample_rate};
    plan->encoder->bit_rate = 64000 * (channels > 6 ? 6 : channels < 2 ? 2 : channels);
    error = av_channel_layout_copy(&plan->encoder->ch_layout, &layout);
    if (error < 0) { av_channel_layout_uninit(&layout); return error; }
    if (output->oformat->flags & AVFMT_GLOBALHEADER) plan->encoder->flags |= AV_CODEC_FLAG_GLOBAL_HEADER;
    error = avcodec_open2(plan->encoder, encoder, NULL);
    if (error < 0) { av_channel_layout_uninit(&layout); return error; }

    error = swr_alloc_set_opts2(&plan->resampler, &plan->encoder->ch_layout, AV_SAMPLE_FMT_FLTP, sample_rate,
                                &plan->decoder->ch_layout, plan->decoder->sample_fmt, plan->decoder->sample_rate, 0, NULL);
    av_channel_layout_uninit(&layout);
    if (error < 0) return error;
    error = swr_init(plan->resampler);
    if (error < 0) return error;

    plan->fifo = av_audio_fifo_alloc(AV_SAMPLE_FMT_FLTP, plan->encoder->ch_layout.nb_channels, 1);
    plan->decoded = av_frame_alloc();
    plan->converted = av_frame_alloc();
    if (!plan->fifo || !plan->decoded || !plan->converted) return AVERROR(ENOMEM);

    out->time_base = plan->encoder->time_base;
    return avcodec_parameters_from_context(out->codecpar, plan->encoder);
}

/// Writes every full frame the fifo holds (all of it when `flush`).
static int encode_from_fifo(StreamPlan *plan, AVFormatContext *output, AVStream *out, int flush, AVPacket *packet) {
    int frame_size = plan->encoder->frame_size > 0 ? plan->encoder->frame_size : 1024;
    while (av_audio_fifo_size(plan->fifo) >= frame_size || (flush && av_audio_fifo_size(plan->fifo) > 0)) {
        int count = av_audio_fifo_size(plan->fifo) < frame_size ? av_audio_fifo_size(plan->fifo) : frame_size;
        AVFrame *frame = av_frame_alloc();
        if (!frame) return AVERROR(ENOMEM);
        frame->nb_samples = count;
        frame->format = AV_SAMPLE_FMT_FLTP;
        frame->sample_rate = plan->encoder->sample_rate;
        av_channel_layout_copy(&frame->ch_layout, &plan->encoder->ch_layout);
        int error = av_frame_get_buffer(frame, 0);
        if (error >= 0 && av_audio_fifo_read(plan->fifo, (void **)frame->data, count) < count) error = AVERROR_UNKNOWN;
        if (error < 0) { av_frame_free(&frame); return error; }
        frame->pts = plan->next_pts;
        plan->next_pts += count;
        error = avcodec_send_frame(plan->encoder, frame);
        av_frame_free(&frame);
        if (error < 0) return error;
        while ((error = avcodec_receive_packet(plan->encoder, packet)) >= 0) {
            packet->stream_index = out->index;
            av_packet_rescale_ts(packet, plan->encoder->time_base, out->time_base);
            error = av_interleaved_write_frame(output, packet);
            if (error < 0) return error;
        }
        if (error != AVERROR(EAGAIN) && error != AVERROR_EOF) return error;
    }
    return 0;
}

/// Feeds decoded frames through the resampler into the fifo.
static int convert_decoded(StreamPlan *plan) {
    int error;
    while ((error = avcodec_receive_frame(plan->decoder, plan->decoded)) >= 0) {
        if (!plan->started) {
            // Keep the track in step with the video: its first sample sits where the first decoded one did.
            if (plan->decoded->pts != AV_NOPTS_VALUE) {
                plan->next_pts = av_rescale_q(plan->decoded->pts, plan->decoder->pkt_timebase, plan->encoder->time_base);
                if (plan->next_pts < 0) plan->next_pts = 0;
            }
            plan->started = 1;
        }
        plan->converted->nb_samples = 0;
        int out_samples = swr_get_out_samples(plan->resampler, plan->decoded->nb_samples);
        plan->converted->format = AV_SAMPLE_FMT_FLTP;
        plan->converted->sample_rate = plan->encoder->sample_rate;
        av_channel_layout_copy(&plan->converted->ch_layout, &plan->encoder->ch_layout);
        plan->converted->nb_samples = out_samples;
        error = av_frame_get_buffer(plan->converted, 0);
        if (error < 0) return error;
        int got = swr_convert(plan->resampler, plan->converted->data, out_samples, (const uint8_t **)plan->decoded->extended_data, plan->decoded->nb_samples);
        if (got < 0) { av_frame_unref(plan->converted); av_frame_unref(plan->decoded); return got; }
        error = av_audio_fifo_write(plan->fifo, (void **)plan->converted->data, got);
        av_frame_unref(plan->converted);
        av_frame_unref(plan->decoded);
        if (error < 0) return error;
    }
    return error == AVERROR(EAGAIN) || error == AVERROR_EOF ? 0 : error;
}

FFRemuxResult ff_remux(const char *source, const char *target, FFRemuxProgress progress, void *context, char *message, size_t message_size) {
    AVFormatContext *input = NULL, *output = NULL;
    StreamPlan *plan = NULL;
    AVPacket *packet = NULL;
    FFRemuxResult result = FFRemuxFailed;
    int error = avformat_open_input(&input, source, NULL, NULL);
    if (error < 0) { set_message(message, message_size, "Cannot open the file", error); return FFRemuxFailed; }
    error = avformat_find_stream_info(input, NULL);
    if (error < 0) { set_message(message, message_size, "Cannot read the streams", error); goto done; }

    // Decide what to do with each stream, and whether the file needs touching at all.
    unsigned count = input->nb_streams;
    plan = av_calloc(count, sizeof(StreamPlan));
    if (!plan) goto done;
    int needs_work = !container_is_playable(input);
    int video_streams = 0;
    for (unsigned i = 0; i < count; i++) {
        AVCodecParameters *params = input->streams[i]->codecpar;
        plan[i].out_index = -1;
        if (params->codec_type == AVMEDIA_TYPE_VIDEO && !(input->streams[i]->disposition & AV_DISPOSITION_ATTACHED_PIC)) {
            if (!ff_can_play_video(avcodec_get_name(params->codec_id))) {
                set_message(message, message_size, avcodec_get_name(params->codec_id), 0);
                result = FFRemuxUnsupportedVideo;
                goto done;
            }
            if (video_streams++ == 0) plan[i].out_index = 0; // only the main video: other video streams are covers or extras
        } else if (params->codec_type == AVMEDIA_TYPE_AUDIO) {
            plan[i].out_index = 0;
            if (!audio_is_playable(params->codec_id)) { plan[i].transcode = 1; needs_work = 1; }
        } else if (params->codec_type == AVMEDIA_TYPE_SUBTITLE || params->codec_type == AVMEDIA_TYPE_ATTACHMENT) {
            if (!container_is_playable(input)) needs_work = 1;
        }
    }
    if (!needs_work) { result = FFRemuxNotNeeded; goto done; }

    error = avformat_alloc_output_context2(&output, NULL, "mp4", target);
    if (error < 0) { set_message(message, message_size, "Cannot start the new file", error); goto done; }
    output->avoid_negative_ts = AVFMT_AVOID_NEG_TS_MAKE_ZERO;

    int next_out = 0;
    for (unsigned i = 0; i < count; i++) {
        if (plan[i].out_index < 0) continue;
        AVStream *in = input->streams[i];
        AVStream *out = avformat_new_stream(output, NULL);
        if (!out) { error = AVERROR(ENOMEM); goto done; }
        plan[i].out_index = next_out++;
        av_dict_copy(&out->metadata, in->metadata, 0);
        out->disposition = in->disposition;
        if (plan[i].transcode) {
            error = open_audio_chain(&plan[i], in, out, output);
            if (error < 0) { set_message(message, message_size, "Cannot convert the audio", error); goto done; }
        } else {
            error = avcodec_parameters_copy(out->codecpar, in->codecpar);
            if (error < 0) goto done;
            out->time_base = in->time_base;
            out->codecpar->codec_tag = 0;
            if (in->codecpar->codec_id == AV_CODEC_ID_HEVC) out->codecpar->codec_tag = MKTAG('h', 'v', 'c', '1');
            if (in->codecpar->codec_id == AV_CODEC_ID_EAC3) out->codecpar->codec_tag = MKTAG('e', 'c', '-', '3');
        }
    }
    av_dict_copy(&output->metadata, input->metadata, 0);

    if (!(output->oformat->flags & AVFMT_NOFILE)) {
        error = avio_open(&output->pb, target, AVIO_FLAG_WRITE);
        if (error < 0) { set_message(message, message_size, "Cannot write the new file", error); goto done; }
    }
    error = avformat_write_header(output, NULL);
    if (error < 0) { set_message(message, message_size, "Cannot write the new file", error); goto done; }

    packet = av_packet_alloc();
    if (!packet) goto done;
    double duration = input->duration > 0 ? (double)input->duration / AV_TIME_BASE : 0;
    int stopped = 0;
    while ((error = av_read_frame(input, packet)) >= 0) {
        StreamPlan *p = &plan[packet->stream_index];
        AVStream *in = input->streams[packet->stream_index];
        if (p->out_index < 0) { av_packet_unref(packet); continue; }
        AVStream *out = output->streams[p->out_index];
        if (duration > 0 && packet->pts != AV_NOPTS_VALUE && progress) {
            double fraction = av_q2d(in->time_base) * (double)packet->pts / duration;
            if (progress(context, fraction < 0 ? 0 : fraction > 1 ? 1 : fraction)) { stopped = 1; av_packet_unref(packet); break; }
        }
        if (p->transcode) {
            error = avcodec_send_packet(p->decoder, packet);
            av_packet_unref(packet);
            // A damaged packet is skipped, as a player would.
            if (error < 0 && error != AVERROR_INVALIDDATA) { set_message(message, message_size, "Cannot decode the audio", error); goto done; }
            error = convert_decoded(p);
            if (error >= 0) error = encode_from_fifo(p, output, out, 0, packet);
            if (error < 0 && error != AVERROR_INVALIDDATA) { set_message(message, message_size, "Cannot convert the audio", error); goto done; }
        } else {
            packet->stream_index = out->index;
            av_packet_rescale_ts(packet, in->time_base, out->time_base);
            packet->pos = -1;
            error = av_interleaved_write_frame(output, packet);
            if (error < 0) { set_message(message, message_size, "Cannot write the new file", error); goto done; }
        }
    }
    if (stopped) { result = FFRemuxStopped; goto done; }
    if (error != AVERROR_EOF) { set_message(message, message_size, "Cannot read the file", error); goto done; }

    // Drain what the audio decoders and encoders still hold.
    for (unsigned i = 0; i < count; i++) {
        StreamPlan *p = &plan[i];
        if (!p->transcode || p->out_index < 0) continue;
        avcodec_send_packet(p->decoder, NULL);
        error = convert_decoded(p);
        if (error >= 0) error = encode_from_fifo(p, output, output->streams[p->out_index], 1, packet);
        if (error >= 0) {
            avcodec_send_frame(p->encoder, NULL);
            while ((error = avcodec_receive_packet(p->encoder, packet)) >= 0) {
                packet->stream_index = p->out_index;
                av_packet_rescale_ts(packet, p->encoder->time_base, output->streams[p->out_index]->time_base);
                error = av_interleaved_write_frame(output, packet);
                if (error < 0) break;
            }
            if (error == AVERROR_EOF || error == AVERROR(EAGAIN)) error = 0;
        }
        if (error < 0) { set_message(message, message_size, "Cannot finish the audio", error); goto done; }
    }
    error = av_write_trailer(output);
    if (error < 0) { set_message(message, message_size, "Cannot finish the new file", error); goto done; }
    if (progress) progress(context, 1.0);
    result = FFRemuxConverted;

done:
    av_packet_free(&packet);
    if (plan) free_plan(plan, input ? input->nb_streams : 0);
    if (output) {
        if (output->pb && !(output->oformat->flags & AVFMT_NOFILE)) avio_closep(&output->pb);
        avformat_free_context(output);
    }
    avformat_close_input(&input);
    if (result == FFRemuxFailed && message && message_size && message[0] == 0) set_message(message, message_size, "Conversion failed", error < 0 ? error : 0);
    return result;
}
