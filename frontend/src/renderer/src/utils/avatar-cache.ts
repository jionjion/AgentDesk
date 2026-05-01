const AVATAR_CACHE_KEY = 'avatar_cache'
const AVATAR_URL_KEY = 'avatar_url'

/** 将远程头像图片转为 base64 并缓存到 localStorage */
export async function cacheAvatar(url: string): Promise<string> {
    if (!url) return ''

    // 如果 URL 没变，直接返回缓存
    const cachedUrl = localStorage.getItem(AVATAR_URL_KEY)
    const cachedData = localStorage.getItem(AVATAR_CACHE_KEY)
    if (cachedUrl === url && cachedData) {
        return cachedData
    }

    // 下载图片并转为 base64
    try {
        const res = await fetch(url)
        const blob = await res.blob()
        const base64 = await blobToBase64(blob)
        localStorage.setItem(AVATAR_URL_KEY, url)
        localStorage.setItem(AVATAR_CACHE_KEY, base64)
        return base64
    } catch {
        // 网络失败时返回原始 URL
        return url
    }
}

/** 从 localStorage 读取缓存的头像 base64 */
export function getCachedAvatar(): string {
    return localStorage.getItem(AVATAR_CACHE_KEY) || ''
}

/** 清除头像缓存（登出时调用） */
export function clearAvatarCache() {
    localStorage.removeItem(AVATAR_CACHE_KEY)
    localStorage.removeItem(AVATAR_URL_KEY)
}

function blobToBase64(blob: Blob): Promise<string> {
    return new Promise((resolve, reject) => {
        const reader = new FileReader()
        reader.onload = () => resolve(reader.result as string)
        reader.onerror = reject
        reader.readAsDataURL(blob)
    })
}
