import { beforeEach, describe, expect, it } from 'vitest'
import * as recordingMedia from '@/composables/recordingMedia'

describe('recordingMedia', () => {
    beforeEach(() => {
        window.localStorage.clear()
        delete window.__UNIRHY_RUNTIME__
    })

    it('normalizes shared recording display fields', () => {
        const recordings = recordingMedia.normalizeRecordings([
            {
                id: 1,
                title: '',
                comment: 'Fallback Title',
                artists: [{ displayName: 'Artist A' }],
                cover: { id: 11, url: '/api/media/11?_sig=abc&_exp=9999999999' },
                assets: [
                    {
                        mediaFile: {
                            id: 21,
                            mimeType: 'audio/mpeg',
                            objectKey: 'track-a.mp3',
                            url: '/api/media/21?_sig=def&_exp=9999999999',
                        },
                    },
                ],
            },
        ])

        expect(recordings).toEqual([
            {
                id: 1,
                title: 'Fallback Title',
                artist: 'Artist A',
                cover: '/api/media/11?_sig=abc&_exp=9999999999',
            },
        ])
    })

    it('picks initial recording ids according to strategy', () => {
        expect(
            recordingMedia.pickInitialRecordingId(
                [
                    { id: 1, assets: [], defaultInWork: false },
                    {
                        id: 2,
                        assets: [
                            {
                                mediaFile: {
                                    id: 21,
                                    mimeType: 'audio/mpeg',
                                    objectKey: 'track-a.mp3',
                                    url: '/api/media/21',
                                },
                            },
                        ],
                        defaultInWork: false,
                    },
                ],
                'first-playable',
            ),
        ).toBe(2)

        expect(
            recordingMedia.pickInitialRecordingId(
                [
                    {
                        id: 1,
                        assets: [
                            {
                                mediaFile: {
                                    id: 21,
                                    mimeType: 'audio/mpeg',
                                    objectKey: 'track-a.mp3',
                                    url: '/api/media/21',
                                },
                            },
                        ],
                        defaultInWork: false,
                    },
                    { id: 2, assets: [], defaultInWork: true },
                ],
                'default-first',
            ),
        ).toBe(2)
    })

    it('does not export legacy resolveAudio wrapper', () => {
        expect('resolveAudio' in recordingMedia).toBe(false)
    })

    it('uses presigned url from cover and mediaFile in tauri runtime', () => {
        window.__UNIRHY_RUNTIME__ = {
            apiBaseUrl: 'http://127.0.0.1:34855',
            platform: 'web',
        }

        expect(
            recordingMedia.resolveCover({
                url: '/api/media/11?_sig=abc&_exp=9999999999',
            }),
        ).toBe('http://127.0.0.1:34855/api/media/11?_sig=abc&_exp=9999999999')

        expect(
            recordingMedia.resolvePlayableAudio([
                {
                    mediaFile: {
                        id: 21,
                        mimeType: 'audio/mpeg',
                        objectKey: 'track-a.mp3',
                        url: '/api/media/21?_sig=def&_exp=9999999999',
                    },
                },
            ]),
        ).toEqual({
            src: 'http://127.0.0.1:34855/api/media/21?_sig=def&_exp=9999999999',
            mediaFileId: 21,
        })
    })

    it('returns empty string when cover has no url', () => {
        expect(recordingMedia.resolveCover(null)).toBe('')
        expect(recordingMedia.resolveCover(undefined)).toBe('')
        expect(recordingMedia.resolveCover({})).toBe('')
    })

    it('returns undefined when no audio asset has url', () => {
        expect(
            recordingMedia.resolvePlayableAudio([
                {
                    mediaFile: {
                        id: 21,
                        mimeType: 'audio/mpeg',
                        objectKey: 'track-a.mp3',
                    },
                },
            ]),
        ).toBeUndefined()
    })

    it('prefers the configured MIME over the first playable asset', () => {
        expect(
            recordingMedia.resolvePlayableAudio(
                [
                    {
                        mediaFile: {
                            id: 21,
                            mimeType: 'audio/mpeg',
                            objectKey: 'track-a.mp3',
                            url: '/api/media/21',
                        },
                    },
                    {
                        mediaFile: {
                            id: 22,
                            mimeType: 'audio/flac',
                            objectKey: 'track-a.flac',
                            url: '/api/media/22',
                        },
                    },
                ],
                'audio/flac',
            ),
        ).toEqual({
            src: '/api/media/22',
            mediaFileId: 22,
        })
    })

    it('falls back to the first playable asset when the preferred MIME is missing', () => {
        expect(
            recordingMedia.resolvePlayableAudio(
                [
                    {
                        mediaFile: {
                            id: 21,
                            mimeType: 'audio/mpeg',
                            objectKey: 'track-a.mp3',
                            url: '/api/media/21',
                        },
                    },
                    {
                        mediaFile: {
                            id: 22,
                            mimeType: 'audio/flac',
                            objectKey: 'track-a.flac',
                            url: '/api/media/22',
                        },
                    },
                ],
                'audio/opus',
            ),
        ).toEqual({
            src: '/api/media/21',
            mediaFileId: 21,
        })
    })

    it('matches preferred MIME after trimming and lowercasing', () => {
        expect(
            recordingMedia.resolvePlayableAudio(
                [
                    {
                        mediaFile: {
                            id: 21,
                            mimeType: 'audio/mpeg',
                            objectKey: 'track-a.mp3',
                            url: '/api/media/21',
                        },
                    },
                    {
                        mediaFile: {
                            id: 22,
                            mimeType: ' Audio/FLAC ',
                            objectKey: 'track-a.flac',
                            url: '/api/media/22',
                        },
                    },
                ],
                '  audio/flac  ',
            ),
        ).toEqual({
            src: '/api/media/22',
            mediaFileId: 22,
        })
    })
})
