// 无图时的占位底色：低饱和中性色，配合白色首字，避免彩色渐变抢画面
const GRADIENTS = {
  荤菜: 'linear-gradient(150deg, #d9c3ae 0%, #b99a80 100%)',
  素菜: 'linear-gradient(150deg, #cfd8c6 0%, #a8b79b 100%)',
  主食: 'linear-gradient(150deg, #e0d3bf 0%, #c0ac91 100%)',
  小吃: 'linear-gradient(150deg, #d3ceDA 0%, #aaa3b8 100%)',
  饮品: 'linear-gradient(150deg, #c9d5dd 0%, #9fb0bd 100%)',
  甜品: 'linear-gradient(150deg, #e2d0d4 0%, #bfa3aa 100%)'
}

export function coverGradient(category) {
  return GRADIENTS[category] || GRADIENTS['主食']
}
