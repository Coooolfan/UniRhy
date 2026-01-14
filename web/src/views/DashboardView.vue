<script setup lang="ts">
import { ref } from 'vue'
import {
    Play,
    SkipBack,
    SkipForward,
    Search,
    Heart,
    MoreHorizontal,
    Volume2,
} from 'lucide-vue-next'

const activeTab = ref('发现')

// Mock Data
const featuredAlbum = {
    title: 'Nocturnes, Op. 9',
    artist: 'Frédéric Chopin',
    year: '1832',
    cover: 'https://picsum.photos/seed/chopin/600/600',
}

const recentAlbums = [
    {
        title: 'Kind of Blue',
        artist: 'Miles Davis',
        cover: 'https://picsum.photos/seed/jazz1/400/400',
    },
    {
        title: 'A Love Supreme',
        artist: 'John Coltrane',
        cover: 'https://picsum.photos/seed/jazz2/400/400',
    },
    {
        title: 'Time Out',
        artist: 'Dave Brubeck',
        cover: 'https://picsum.photos/seed/jazz3/400/400',
    },
    {
        title: 'Blue Train',
        artist: 'John Coltrane',
        cover: 'https://picsum.photos/seed/jazz4/400/400',
    },
    {
        title: 'Mingus Ah Um',
        artist: 'Charles Mingus',
        cover: 'https://picsum.photos/seed/jazz5/400/400',
    },
    {
        title: "Somethin' Else",
        artist: 'Cannonball Adderley',
        cover: 'https://picsum.photos/seed/jazz6/400/400',
    },
    {
        title: "Moanin'",
        artist: 'Art Blakey',
        cover: 'https://picsum.photos/seed/jazz7/400/400',
    },
    {
        title: 'Saxophone Colossus',
        artist: 'Sonny Rollins',
        cover: 'https://picsum.photos/seed/jazz8/400/400',
    },
    {
        title: 'Go',
        artist: 'Dexter Gordon',
        cover: 'https://picsum.photos/seed/jazz9/400/400',
    },
    {
        title: 'The Sidewinder',
        artist: 'Lee Morgan',
        cover: 'https://picsum.photos/seed/jazz10/400/400',
    },
]

const categories = ['古典', '爵士', '极简主义', '环境音', '器乐']

const navItems = ['发现', '阅览室', '收藏', '最近播放']
const playlists = ['雨天巴赫', '咖啡馆噪音', '深夜阅读']
</script>

<template>
    <div
        class="flex h-screen w-full bg-[#EBE7E0] font-sans text-[#4A4A4A] overflow-hidden relative selection:bg-[#D4C5B0] selection:text-white"
    >
        <!-- Noise Texture Overlay -->
        <div
            class="absolute inset-0 opacity-[0.03] pointer-events-none mix-blend-multiply"
            :style="{
                backgroundImage: `url(&quot;data:image/svg+xml,%3Csvg viewBox='0 0 200 200' xmlns='http://www.w3.org/2000/svg'%3E%3Cfilter id='noiseFilter'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='0.65' numOctaves='3' stitchTiles='stitch'/%3E%3C/filter%3E%3Crect width='100%25' height='100%25' filter='url(%23noiseFilter)'/%3E%3C/svg%3E&quot;)`,
            }"
        ></div>

        <!-- Sidebar -->
        <aside class="w-64 flex flex-col pt-12 pl-10 pr-6 z-10 md:flex">
            <div class="mb-12">
                <h1 class="text-3xl font-serif tracking-tight text-[#2C2C2C]">Melody.</h1>
            </div>

            <nav class="space-y-6 flex-1">
                <div
                    v-for="item in navItems"
                    :key="item"
                    class="text-sm cursor-pointer transition-colors duration-300"
                    :class="
                        activeTab === item
                            ? 'text-[#C27E46] font-medium'
                            : 'text-[#8A857D] hover:text-[#5E5950]'
                    "
                    @click="activeTab = item"
                >
                    {{ item }}
                </div>
            </nav>

            <div class="pb-32">
                <div
                    class="text-xs text-[#9C968B] uppercase tracking-widest mb-4 border-b border-[#D6D1C7] pb-2"
                >
                    我的歌单
                </div>
                <ul class="space-y-3 text-sm text-[#6B665E]">
                    <li
                        v-for="playlist in playlists"
                        :key="playlist"
                        class="hover:text-[#C27E46] cursor-pointer transition-colors"
                    >
                        {{ playlist }}
                    </li>
                </ul>
            </div>
        </aside>

        <!-- Main Content -->
        <main
            class="flex-1 flex flex-col h-full relative z-10 overflow-y-auto pt-8 px-8 pb-32 no-scrollbar"
        >
            <!-- Top Search Bar -->
            <div class="flex justify-between items-center mb-10 px-2">
                <div class="flex items-center space-x-2 border-b border-[#D6D1C7] pb-1 w-64">
                    <Search :size="16" class="text-[#9C968B]" />
                    <input
                        type="text"
                        placeholder="搜索艺术家、作品..."
                        class="bg-transparent border-none outline-none text-sm w-full placeholder-[#9C968B] text-[#4A4A4A] focus:ring-0"
                    />
                </div>
                <div
                    class="w-8 h-8 rounded-full bg-[#D6D1C7] overflow-hidden cursor-pointer opacity-80 hover:opacity-100 transition-opacity ring-2 ring-white ring-offset-1 ring-offset-[#EBE7E0]"
                >
                    <img
                        src="https://picsum.photos/seed/user/100/100"
                        alt="User"
                        class="w-full h-full object-cover"
                    />
                </div>
            </div>

            <!-- Hero Section -->
            <div class="mb-14 relative px-2">
                <h2 class="text-xl font-serif mb-6 text-[#2C2C2C]">每日精选</h2>

                <div class="relative h-80 w-full">
                    <!-- Bottom Stack Layers -->
                    <div
                        class="absolute top-4 left-4 -right-2.5 -bottom-2.5 bg-[#F0EBE3] shadow-sm transform rotate-1 rounded-sm border border-[#E6E1D8]"
                    ></div>
                    <div
                        class="absolute top-2 left-2 -right-1.25 -bottom-1.25 bg-[#F5F2EB] shadow-md transform -rotate-1 rounded-sm border border-[#E6E1D8]"
                    ></div>

                    <!-- Top Card -->
                    <div
                        class="absolute inset-0 bg-[#FCFBF9] shadow-[0_10px_30px_-10px_rgba(168,160,149,0.4)] rounded-sm flex overflow-hidden border border-white"
                    >
                        <!-- Album Cover -->
                        <div
                            class="h-full aspect-square bg-[#D6D2C9] relative flex items-center justify-center group cursor-pointer overflow-hidden border-r border-[#EBE7E0] shrink-0"
                        >
                            <img
                                :src="featuredAlbum.cover"
                                alt="Album Cover"
                                class="absolute inset-0 w-full h-full object-cover opacity-90 transition-transform duration-700 group-hover:scale-105"
                            />

                            <!-- Play Overlay -->
                            <div
                                class="absolute inset-0 bg-black/20 opacity-0 group-hover:opacity-100 transition-opacity flex items-center justify-center backdrop-blur-[1px]"
                            >
                                <div
                                    class="w-14 h-14 bg-[#F5F2EB] rounded-full flex items-center justify-center shadow-lg transform translate-y-4 group-hover:translate-y-0 transition-all duration-300"
                                >
                                    <Play :size="24" fill="#C27E46" class="text-[#C27E46] ml-1" />
                                </div>
                            </div>
                        </div>

                        <!-- Content -->
                        <div
                            class="flex-1 p-12 flex flex-col justify-center bg-[url('https://www.transparenttextures.com/patterns/cream-paper.png')] relative overflow-hidden"
                        >
                            <!-- Background Decoration -->
                            <div
                                class="absolute -right-32 top-1/2 -translate-y-1/2 w-96 h-96 opacity-[0.06] pointer-events-none select-none"
                            >
                                <svg viewBox="0 0 200 200" xmlns="http://www.w3.org/2000/svg">
                                    <circle
                                        cx="100"
                                        cy="100"
                                        r="75"
                                        fill="none"
                                        stroke="#2C2C2C"
                                        stroke-width="15"
                                    />
                                    <circle
                                        cx="100"
                                        cy="100"
                                        r="15"
                                        fill="none"
                                        stroke="#2C2C2C"
                                        stroke-width="15"
                                    />
                                </svg>
                            </div>

                            <div class="relative z-10">
                                <div
                                    class="text-xs uppercase tracking-widest text-[#9C968B] mb-4 font-medium"
                                >
                                    Editor's Choice
                                </div>
                                <h3
                                    class="text-5xl font-serif text-[#2C2C2C] mb-4 tracking-tight leading-tight"
                                >
                                    {{ featuredAlbum.title }}
                                </h3>
                                <p
                                    class="text-[#8A857D] text-lg mb-10 font-serif italic flex items-center"
                                >
                                    <span class="w-8 h-px bg-[#C27E46] mr-3 inline-block"></span>
                                    {{ featuredAlbum.artist }}
                                </p>
                                <div class="flex items-center space-x-6">
                                    <button
                                        class="px-8 py-3 border border-[#C27E46] text-[#C27E46] text-sm hover:bg-[#C27E46] hover:text-white transition-all duration-500 rounded-sm font-medium tracking-wide uppercase"
                                    >
                                        立即播放
                                    </button>
                                    <button
                                        class="text-[#9C968B] hover:text-[#C27E46] transition-colors p-2"
                                    >
                                        <Heart :size="24" />
                                    </button>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            </div>

            <!-- Categories -->
            <div class="flex space-x-8 mb-10 overflow-x-auto px-2 py-4 no-scrollbar items-center">
                <span
                    v-for="(cat, idx) in categories"
                    :key="idx"
                    class="text-base text-[#8A857D] hover:text-[#C27E46] cursor-pointer whitespace-nowrap transition-colors border-b-2 border-transparent hover:border-[#C27E46] pb-1 leading-relaxed"
                >
                    {{ cat }}
                </span>
            </div>

            <!-- Album Grid -->
            <div class="mb-8">
                <div
                    class="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6 gap-8 px-2"
                >
                    <div
                        v-for="(album, idx) in recentAlbums"
                        :key="idx"
                        class="group cursor-pointer"
                    >
                        <div
                            class="aspect-square bg-gray-200 mb-4 shadow-[0_4px_12px_-4px_rgba(168,160,149,0.3)] group-hover:shadow-[0_12px_24px_-8px_rgba(168,160,149,0.5)] group-hover:-translate-y-1 transition-all duration-500 rounded-sm relative border-[5px] border-white overflow-hidden"
                        >
                            <img
                                :src="album.cover"
                                :alt="album.title"
                                class="w-full h-full object-cover filter sepia-[0.2] group-hover:sepia-0 transition-all duration-500"
                            />
                            <!-- Floating Play Button -->
                            <div
                                class="absolute bottom-3 right-3 w-8 h-8 bg-white/90 rounded-full flex items-center justify-center opacity-0 group-hover:opacity-100 transition-all duration-300 shadow-sm translate-y-2 group-hover:translate-y-0"
                            >
                                <Play :size="12" fill="#C27E46" class="text-[#C27E46] `ml-px" />
                            </div>
                        </div>
                        <h4
                            class="font-serif text-[#2C2C2C] text-lg leading-tight group-hover:text-[#C27E46] transition-colors line-clamp-1"
                        >
                            {{ album.title }}
                        </h4>
                        <p class="text-xs text-[#9C968B] mt-1">{{ album.artist }}</p>
                    </div>
                </div>
            </div>
        </main>

        <!-- Bottom Player Bar -->
        <div class="absolute bottom-6 left-6 right-6 z-50">
            <div
                class="bg-[#FCFBF9] h-20 rounded-sm shadow-[0_8px_30px_rgba(140,130,115,0.2)] border border-[#EBE7E0] flex items-center px-6 justify-between backdrop-blur-xl bg-opacity-95"
            >
                <!-- Now Playing Info -->
                <div class="flex items-center w-1/4">
                    <div
                        class="w-12 h-12 bg-[#2C2C2C] shadow-md border-[3px] border-white mr-4 shrink-"
                    >
                        <img
                            :src="featuredAlbum.cover"
                            alt="Now Playing"
                            class="w-full h-full object-cover"
                        />
                    </div>
                    <div class="overflow-hidden">
                        <div class="text-sm font-serif text-[#2C2C2C] truncate">
                            Ballade No. 1 in G Minor
                        </div>
                        <div class="text-xs text-[#9C968B] truncate">Chopin</div>
                    </div>
                </div>

                <!-- Controls -->
                <div class="flex flex-col items-center w-2/4 px-4">
                    <div class="flex items-center space-x-8 mb-2">
                        <SkipBack
                            :size="18"
                            class="text-[#9C968B] cursor-pointer hover:text-[#4A4A4A]"
                        />
                        <div
                            class="w-10 h-10 rounded-full border border-[#C27E46] flex items-center justify-center cursor-pointer hover:bg-[#C27E46] group transition-all shadow-sm"
                        >
                            <Play
                                :size="16"
                                class="text-[#C27E46] ml-1 group-hover:text-white group-hover:fill-white"
                            />
                        </div>
                        <SkipForward
                            :size="18"
                            class="text-[#9C968B] cursor-pointer hover:text-[#4A4A4A]"
                        />
                    </div>
                    <!-- Progress Bar -->
                    <div
                        class="w-full max-w-2xl h-0.5 relative group cursor-pointer py-2 flex items-center"
                    >
                        <!-- Track Background -->
                        <div class="w-full h-0.5 bg-[#EBE7E0] absolute left-0"></div>
                        <!-- Progress Fill -->
                        <div class="h-0.5 w-1/3 bg-[#C27E46] absolute left-0"></div>
                        <!-- Knob -->
                        <div
                            class="w-2 h-2 bg-[#C27E46] rounded-full absolute left-1/3 opacity-0 group-hover:opacity-100 transition-opacity shadow-sm transform -translate-x-1/2"
                        ></div>
                    </div>
                </div>

                <!-- Volume & Extras -->
                <div class="flex items-center justify-end w-1/4 space-x-4">
                    <MoreHorizontal
                        :size="18"
                        class="text-[#9C968B] cursor-pointer hover:text-[#4A4A4A]"
                    />
                    <div class="flex items-center space-x-2 group cursor-pointer">
                        <Volume2 :size="18" class="text-[#9C968B] group-hover:text-[#4A4A4A]" />
                        <div class="w-16 h-0.5 bg-[#EBE7E0] relative">
                            <div class="w-2/3 h-full bg-[#9C968B]"></div>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    </div>
</template>

<style>
/* Hide scrollbar for Chrome, Safari and Opera */
.no-scrollbar::-webkit-scrollbar {
    display: none;
}

/* Hide scrollbar for IE, Edge and Firefox */
.no-scrollbar {
    -ms-overflow-style: none; /* IE and Edge */
    scrollbar-width: none; /* Firefox */
}
</style>
