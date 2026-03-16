import http, { type IncomingMessage } from 'node:http'
import path from 'node:path'

import finalhandler from 'finalhandler'
import { createProxyServer } from 'http-proxy'
import serveStatic from 'serve-static'

export type StartProxyServerOptions = {
    devServerUrl?: string
    getBackendUrl: () => string
    staticDir?: string
}

export type StartedProxyServer = {
    close: () => Promise<void>
    port: number
    url: string
}

const LOCALHOST_ORIGIN = 'http://127.0.0.1'

const normalizeBaseUrl = (value: string) => {
    return value.endsWith('/') ? value.slice(0, -1) : value
}

const toWebSocketUrl = (value: string) => {
    return normalizeBaseUrl(value).replace(/^http/i, 'ws')
}

const isRoutePrefix = (pathname: string, prefix: string) => {
    return pathname === prefix || pathname.startsWith(`${prefix}/`)
}

const getPathname = (requestUrl: string | undefined) => {
    return new URL(requestUrl ?? '/', LOCALHOST_ORIGIN).pathname
}

const shouldServeSpaFallback = (request: IncomingMessage) => {
    if (request.method !== 'GET' && request.method !== 'HEAD') {
        return false
    }

    return path.extname(getPathname(request.url)) === ''
}

const isHttpResponse = (
    value: http.ServerResponse | import('node:net').Socket,
): value is http.ServerResponse => {
    return 'writeHead' in value
}

export const startProxyServer = async ({
    staticDir,
    devServerUrl,
    getBackendUrl,
}: StartProxyServerOptions): Promise<StartedProxyServer> => {
    if (devServerUrl === undefined && staticDir === undefined) {
        throw new Error('Either devServerUrl or staticDir must be provided')
    }

    const normalizedDevServerUrl =
        devServerUrl === undefined ? undefined : normalizeBaseUrl(devServerUrl)
    const resolveBackendUrl = () => {
        return normalizeBaseUrl(getBackendUrl())
    }
    const staticFileHandler =
        staticDir === undefined
            ? undefined
            : serveStatic(staticDir, {
                  fallthrough: true,
                  index: ['index.html'],
              })

    const proxy = createProxyServer()
    proxy.on('error', (_error, _request, responseTarget) => {
        if (responseTarget === undefined) {
            return
        }

        if (isHttpResponse(responseTarget)) {
            if (!responseTarget.headersSent) {
                responseTarget.writeHead(502, {
                    'Content-Type': 'text/plain; charset=utf-8',
                })
            }

            responseTarget.end('Bad Gateway')
            return
        }

        responseTarget.end('HTTP/1.1 502 Bad Gateway\r\n\r\n')
    })

    const server = http.createServer((request, response) => {
        const pathname = getPathname(request.url)

        if (isRoutePrefix(pathname, '/api')) {
            const backendUrl = resolveBackendUrl()
            proxy.web(request, response, {
                changeOrigin: true,
                cookieDomainRewrite: '',
                secure: false,
                target: backendUrl,
                xfwd: true,
            })
            return
        }

        if (isRoutePrefix(pathname, '/ws')) {
            const backendUrl = resolveBackendUrl()
            proxy.web(request, response, {
                changeOrigin: true,
                secure: false,
                target: backendUrl,
                xfwd: true,
            })
            return
        }

        if (normalizedDevServerUrl !== undefined) {
            proxy.web(request, response, {
                changeOrigin: true,
                secure: false,
                target: normalizedDevServerUrl,
                xfwd: true,
            })
            return
        }

        if (staticFileHandler === undefined) {
            finalhandler(request, response)()
            return
        }

        staticFileHandler(request, response, () => {
            if (!shouldServeSpaFallback(request)) {
                finalhandler(request, response)()
                return
            }

            request.url = '/index.html'
            staticFileHandler(request, response, finalhandler(request, response))
        })
    })

    server.on('upgrade', (request, socket, head) => {
        const pathname = getPathname(request.url)

        if (isRoutePrefix(pathname, '/ws')) {
            const backendUrl = resolveBackendUrl()
            proxy.ws(request, socket, head, {
                changeOrigin: true,
                secure: false,
                target: toWebSocketUrl(backendUrl),
                xfwd: true,
            })
            return
        }

        if (normalizedDevServerUrl !== undefined) {
            proxy.ws(request, socket, head, {
                changeOrigin: true,
                secure: false,
                target: toWebSocketUrl(normalizedDevServerUrl),
                xfwd: true,
            })
            return
        }

        socket.destroy()
    })

    await new Promise<void>((resolve, reject) => {
        server.once('error', reject)
        server.listen(0, '127.0.0.1', () => {
            server.off('error', reject)
            resolve()
        })
    })

    const address = server.address()
    if (address === null || typeof address === 'string') {
        throw new Error('Proxy server failed to bind to a TCP port')
    }

    const { port } = address

    return {
        close: () =>
            new Promise<void>((resolve, reject) => {
                proxy.close()
                server.close((error) => {
                    if (error === undefined) {
                        resolve()
                        return
                    }

                    reject(error)
                })
            }),
        port,
        url: `http://127.0.0.1:${port}/`,
    }
}
