import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

vi.mock('@/ApiInstance', async (importOriginal) => {
    const actual = await importOriginal<typeof import('@/ApiInstance')>()
    return {
        ...actual,
        api: {
            albumController: {
                getAlbum: vi.fn(),
            },
            workController: {
                getWorkById: vi.fn(),
            },
        },
    }
})

import { api } from '@/ApiInstance'
import {
    invalidateResolvedPlayableTrack,
    invalidateResolvedPlayableTracksByRecording,
    resetRecordingPlaybackResolverCaches,
    resolveAlbumPlayableTrack,
    resolvePlaybackTrackFromCandidate,
    resolveWorkPlayableTrack,
} from '@/services/recordingPlaybackResolver'
import { useUserStore } from '@/stores/user'

const getAlbumMock = vi.mocked(api.albumController.getAlbum)
const getWorkMock = vi.mocked(api.workController.getWorkById)

const setPreferredAssetFormat = (preferredAssetFormat: string) => {
    const userStore = useUserStore()
    userStore.user = {
        id: 1,
        name: 'Tester',
        email: 'tester@example.com',
        admin: false,
        preferences: {
            preferredAssetFormat,
        },
    }
}

describe('recordingPlaybackResolver', () => {
    beforeEach(() => {
        setActivePinia(createPinia())
        resetRecordingPlaybackResolverCaches()
        getAlbumMock.mockReset()
        getWorkMock.mockReset()
        setPreferredAssetFormat('audio/opus')
    })

    it('reuses cached audio source resolution for the same recording and preferred format', async () => {
        const candidate = {
            id: 7,
            title: 'Track 7',
            artist: 'Artist 7',
            cover: '/cover/7.jpg',
            assets: [
                {
                    mediaFile: {
                        id: 2_071,
                        mimeType: 'audio/mpeg',
                        objectKey: 'track-7.mp3',
                        url: '/api/media/2071',
                    },
                },
            ],
        }

        const firstTrack = await resolvePlaybackTrackFromCandidate(candidate, 'audio/mpeg')
        const secondTrack = await resolvePlaybackTrackFromCandidate(
            {
                ...candidate,
                assets: [
                    {
                        mediaFile: {
                            id: 9_999,
                            mimeType: 'audio/mpeg',
                            objectKey: 'track-7-new.mp3',
                            url: '/api/media/9999',
                        },
                    },
                ],
            },
            'audio/mpeg',
        )

        expect(firstTrack).toEqual(
            expect.objectContaining({
                mediaFileId: 2_071,
                src: '/api/media/2071',
            }),
        )
        expect(secondTrack).toEqual(firstTrack)
    })

    it('uses a new cache entry when the preferred asset format changes', async () => {
        const candidate = {
            id: 8,
            title: 'Track 8',
            artist: 'Artist 8',
            cover: '/cover/8.jpg',
            assets: [
                {
                    mediaFile: {
                        id: 2_081,
                        mimeType: 'audio/mpeg',
                        objectKey: 'track-8.mp3',
                        url: '/api/media/2081',
                    },
                },
                {
                    mediaFile: {
                        id: 2_082,
                        mimeType: 'audio/flac',
                        objectKey: 'track-8.flac',
                        url: '/api/media/2082',
                    },
                },
            ],
        }

        const mp3Track = await resolvePlaybackTrackFromCandidate(candidate, 'audio/mpeg')
        const flacTrack = await resolvePlaybackTrackFromCandidate(candidate, 'audio/flac')

        expect(mp3Track).toEqual(
            expect.objectContaining({
                mediaFileId: 2_081,
                src: '/api/media/2081',
            }),
        )
        expect(flacTrack).toEqual(
            expect.objectContaining({
                mediaFileId: 2_082,
                src: '/api/media/2082',
            }),
        )
    })

    it('caches album playback details per preferred asset format', async () => {
        getAlbumMock.mockResolvedValue({
            id: 101,
            title: 'Album A',
            kind: 'Album',
            comment: 'Album Comment',
            recordings: [
                {
                    id: 501,
                    kind: 'Studio',
                    title: 'Track A',
                    comment: '',
                    durationMs: 200000,
                    defaultInWork: true,
                    assets: [
                        {
                            id: 901,
                            comment: 'Audio mp3',
                            mediaFile: {
                                id: 2_091,
                                sha256: 'hash-2091',
                                mimeType: 'audio/mpeg',
                                size: 123,
                                objectKey: 'track-a.mp3',
                                url: '/api/media/2091',
                            },
                        },
                        {
                            id: 902,
                            comment: 'Audio flac',
                            mediaFile: {
                                id: 2_092,
                                sha256: 'hash-2092',
                                mimeType: 'audio/flac',
                                size: 456,
                                objectKey: 'track-a.flac',
                                url: '/api/media/2092',
                            },
                        },
                    ],
                    artists: [{ id: 1, displayName: 'Artist A', alias: [], comment: '' }],
                    cover: {
                        id: 702,
                        sha256: 'cover-702',
                        objectKey: 'cover-702.jpg',
                        mimeType: 'image/jpeg',
                        size: 456,
                        url: '/api/media/702',
                    },
                },
            ],
        })

        setPreferredAssetFormat('audio/flac')
        const firstTrack = await resolveAlbumPlayableTrack(101, {
            title: 'Album A',
            artist: 'Artist A',
            cover: '/cover/a.jpg',
        })
        const secondTrack = await resolveAlbumPlayableTrack(101, {
            title: 'Album A (Changed Fallback)',
            artist: 'Artist A',
            cover: '/cover/b.jpg',
        })

        expect(getAlbumMock).toHaveBeenCalledTimes(1)
        expect(firstTrack).toEqual(
            expect.objectContaining({
                id: 501,
                mediaFileId: 2_092,
                src: '/api/media/2092',
            }),
        )
        expect(secondTrack).toEqual(firstTrack)

        setPreferredAssetFormat('audio/mpeg')
        const thirdTrack = await resolveAlbumPlayableTrack(101, {
            title: 'Album A',
            artist: 'Artist A',
            cover: '/cover/a.jpg',
        })

        expect(getAlbumMock).toHaveBeenCalledTimes(2)
        expect(thirdTrack).toEqual(
            expect.objectContaining({
                mediaFileId: 2_091,
                src: '/api/media/2091',
            }),
        )
    })

    it('invalidates a cached container track by type and id', async () => {
        getWorkMock
            .mockResolvedValueOnce({
                id: 301,
                title: 'Work A',
                recordings: [
                    {
                        id: 601,
                        kind: 'Studio',
                        title: 'Track A',
                        comment: '',
                        durationMs: 200000,
                        defaultInWork: true,
                        assets: [
                            {
                                id: 911,
                                comment: 'Audio mp3',
                                mediaFile: {
                                    id: 2_111,
                                    sha256: 'hash-2111',
                                    mimeType: 'audio/mpeg',
                                    size: 123,
                                    objectKey: 'track-a.mp3',
                                    url: '/api/media/2111',
                                },
                            },
                        ],
                        artists: [{ id: 1, displayName: 'Artist A', alias: [], comment: '' }],
                        cover: undefined,
                    },
                ],
            })
            .mockResolvedValueOnce({
                id: 301,
                title: 'Work A',
                recordings: [
                    {
                        id: 602,
                        kind: 'Studio',
                        title: 'Track B',
                        comment: '',
                        durationMs: 200000,
                        defaultInWork: true,
                        assets: [
                            {
                                id: 912,
                                comment: 'Audio mp3',
                                mediaFile: {
                                    id: 2_112,
                                    sha256: 'hash-2112',
                                    mimeType: 'audio/mpeg',
                                    size: 123,
                                    objectKey: 'track-b.mp3',
                                    url: '/api/media/2112',
                                },
                            },
                        ],
                        artists: [{ id: 1, displayName: 'Artist A', alias: [], comment: '' }],
                        cover: undefined,
                    },
                ],
            })

        const firstTrack = await resolveWorkPlayableTrack(301, {
            title: 'Work A',
            artist: 'Artist A',
            cover: '/cover/work-a.jpg',
        })
        const cachedTrack = await resolveWorkPlayableTrack(301, {
            title: 'Work A',
            artist: 'Artist A',
            cover: '/cover/work-a.jpg',
        })

        expect(getWorkMock).toHaveBeenCalledTimes(1)
        expect(cachedTrack).toEqual(firstTrack)

        invalidateResolvedPlayableTrack('work', 301)

        const refreshedTrack = await resolveWorkPlayableTrack(301, {
            title: 'Work A',
            artist: 'Artist A',
            cover: '/cover/work-a.jpg',
        })

        expect(getWorkMock).toHaveBeenCalledTimes(2)
        expect(refreshedTrack).toEqual(
            expect.objectContaining({
                id: 602,
                mediaFileId: 2_112,
                src: '/api/media/2112',
            }),
        )
    })

    it('invalidates cached recording sources and dependent album tracks by recording id', async () => {
        const candidate = {
            id: 7,
            title: 'Track 7',
            artist: 'Artist 7',
            cover: '/cover/7.jpg',
            assets: [
                {
                    mediaFile: {
                        id: 2_171,
                        mimeType: 'audio/mpeg',
                        objectKey: 'track-7.mp3',
                        url: '/api/media/2171',
                    },
                },
            ],
        }

        const firstResolvedTrack = await resolvePlaybackTrackFromCandidate(candidate, 'audio/mpeg')
        invalidateResolvedPlayableTracksByRecording(7)
        const refreshedResolvedTrack = await resolvePlaybackTrackFromCandidate(
            {
                ...candidate,
                assets: [
                    {
                        mediaFile: {
                            id: 2_172,
                            mimeType: 'audio/mpeg',
                            objectKey: 'track-7-updated.mp3',
                            url: '/api/media/2172',
                        },
                    },
                ],
            },
            'audio/mpeg',
        )

        expect(firstResolvedTrack).toEqual(
            expect.objectContaining({
                mediaFileId: 2_171,
                src: '/api/media/2171',
            }),
        )
        expect(refreshedResolvedTrack).toEqual(
            expect.objectContaining({
                mediaFileId: 2_172,
                src: '/api/media/2172',
            }),
        )

        getAlbumMock
            .mockResolvedValueOnce({
                id: 401,
                title: 'Album B',
                kind: 'Album',
                comment: '',
                recordings: [
                    {
                        id: 701,
                        kind: 'Studio',
                        title: 'Track B',
                        comment: '',
                        durationMs: 180000,
                        defaultInWork: true,
                        assets: [
                            {
                                id: 913,
                                comment: 'Audio mp3',
                                mediaFile: {
                                    id: 2_211,
                                    sha256: 'hash-2211',
                                    mimeType: 'audio/mpeg',
                                    size: 123,
                                    objectKey: 'track-b.mp3',
                                    url: '/api/media/2211',
                                },
                            },
                        ],
                        artists: [{ id: 1, displayName: 'Artist B', alias: [], comment: '' }],
                        cover: undefined,
                    },
                ],
            })
            .mockResolvedValueOnce({
                id: 401,
                title: 'Album B',
                kind: 'Album',
                comment: '',
                recordings: [
                    {
                        id: 701,
                        kind: 'Studio',
                        title: 'Track B (Updated)',
                        comment: '',
                        durationMs: 180000,
                        defaultInWork: true,
                        assets: [
                            {
                                id: 914,
                                comment: 'Audio mp3 updated',
                                mediaFile: {
                                    id: 2_212,
                                    sha256: 'hash-2212',
                                    mimeType: 'audio/mpeg',
                                    size: 123,
                                    objectKey: 'track-b-updated.mp3',
                                    url: '/api/media/2212',
                                },
                            },
                        ],
                        artists: [{ id: 1, displayName: 'Artist B', alias: [], comment: '' }],
                        cover: undefined,
                    },
                ],
            })

        const firstAlbumTrack = await resolveAlbumPlayableTrack(401, {
            title: 'Album B',
            artist: 'Artist B',
            cover: '/cover/b.jpg',
        })

        invalidateResolvedPlayableTracksByRecording(701)

        const refreshedAlbumTrack = await resolveAlbumPlayableTrack(401, {
            title: 'Album B',
            artist: 'Artist B',
            cover: '/cover/b.jpg',
        })

        expect(getAlbumMock).toHaveBeenCalledTimes(2)
        expect(firstAlbumTrack).toEqual(
            expect.objectContaining({
                mediaFileId: 2_211,
                src: '/api/media/2211',
            }),
        )
        expect(refreshedAlbumTrack).toEqual(
            expect.objectContaining({
                title: 'Track B (Updated)',
                mediaFileId: 2_212,
                src: '/api/media/2212',
            }),
        )
    })
})
