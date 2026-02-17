import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import App from '@/App.vue'

describe('App', () => {
    it('renders router view and audio player', () => {
        const wrapper = mount(App, {
            global: {
                stubs: {
                    RouterView: {
                        template: '<div data-test="router-view" />',
                    },
                    AudioPlayer: {
                        template: '<div data-test="audio-player" />',
                    },
                },
            },
        })

        expect(wrapper.find('[data-test="router-view"]').exists()).toBe(true)
        expect(wrapper.find('[data-test="audio-player"]').exists()).toBe(true)
    })
})
