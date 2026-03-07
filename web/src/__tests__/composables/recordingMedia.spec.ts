import { describe, expect, it } from 'vitest'
import * as recordingMedia from '@/composables/recordingMedia'

describe('recordingMedia', () => {
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
})
