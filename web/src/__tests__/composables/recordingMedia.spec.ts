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
                cover: { id: 11 },
                assets: [
                    {
                        mediaFile: {
                            id: 21,
                            mimeType: 'audio/mpeg',
                            objectKey: 'track-a.mp3',
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
                cover: '/api/media/11',
                audioSrc: '/api/media/21',
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

    it('adds proxy auth token to media urls in tauri runtime', () => {
        window.__UNIRHY_RUNTIME__ = {
            apiBaseUrl: 'http://127.0.0.1:34855',
            platform: 'web',
        }
        window.localStorage.setItem('unirhy.auth-token', 'mobile-token')

        expect(recordingMedia.resolveCover(11)).toBe(
            'http://127.0.0.1:34855/api/media/11?unirhy-proxy-token=mobile-token',
        )
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
        ).toEqual({
            src: 'http://127.0.0.1:34855/api/media/21?unirhy-proxy-token=mobile-token',
            mediaFileId: 21,
        })
    })
})
