/**
 * 上传前图片预处理：
 * - 后端只接受 jpg/jpeg/png/webp/gif 且 multipart 上限 5MB；
 * - 其他格式（bmp/avif/大图等）在浏览器里用 canvas 转成 JPEG，并压到 1600px 内；
 * - 浏览器无法解码的格式（如 HEIC）返回 null，由调用方提示用户转格式。
 */
const ALLOWED = ['jpg', 'jpeg', 'png', 'webp', 'gif']
const MAX_SIZE = 5 * 1024 * 1024

export function prepareImageFile(file) {
  const ext = (file.name.split('.').pop() || '').toLowerCase()
  if (ALLOWED.includes(ext) && file.size <= MAX_SIZE) {
    return Promise.resolve(file)
  }
  return new Promise(resolve => {
    const url = URL.createObjectURL(file)
    const img = new Image()
    img.onload = () => {
      const maxSide = 1600
      const scale = Math.min(1, maxSide / Math.max(img.width, img.height))
      const canvas = document.createElement('canvas')
      canvas.width = Math.max(1, Math.round(img.width * scale))
      canvas.height = Math.max(1, Math.round(img.height * scale))
      canvas.getContext('2d').drawImage(img, 0, 0, canvas.width, canvas.height)
      canvas.toBlob(blob => {
        URL.revokeObjectURL(url)
        if (!blob) {
          resolve(null)
          return
        }
        resolve(new File([blob], 'dish.jpg', { type: 'image/jpeg' }))
      }, 'image/jpeg', 0.85)
    }
    img.onerror = () => {
      URL.revokeObjectURL(url)
      resolve(null)
    }
    img.src = url
  })
}
