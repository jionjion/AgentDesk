/** 判断 contentType 是否为图片 */
export function isImageType(contentType: string): boolean {
    return contentType.startsWith('image/')
}

/** 格式化文件大小 */
export function formatFileSize(bytes: number): string {
    if (bytes < 1024) return bytes + ' B'
    if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
    return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
}
