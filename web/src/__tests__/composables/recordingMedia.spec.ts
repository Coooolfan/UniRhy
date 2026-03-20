import { beforeEach, describe, expect, it } from 'vitest'
import * as recordingMedia from '@/composables/recordingMedia'

describe('recordingMedia', () => {
    beforeEach(() => {
        window.localStorage.clear()
        delete window.__UNIRHY_RUNTIME__
    })

    it('normalizes shared recording playback fields', () => {
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
                audioSrc: '/api/media/21?_sig=def&_exp=9999999999',
                mediaFileId: 21,
            },
        ])
    })

    it('picks initial recording ids according to strategy', () => {
        expect(
            recordingMedia.pickInitialRecordingId(
                [
                    { id: 1, audioSrc: undefined, isDefault: false },
                    { id: 2, audioSrc: '/api/media/2', isDefault: false },
                ],
                'first-playable',
            ),
        ).toBe(2)

        expect(
            recordingMedia.pickInitialRecordingId(
                [
                    { id: 1, audioSrc: '/api/media/1', isDefault: false },
                    { id: 2, audioSrc: undefined, isDefault: true },
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
})
