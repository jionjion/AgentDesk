import axios from 'axios'

const request = axios.create({
  baseURL: 'http://localhost:8080',
  timeout: 10000
})

// 请求拦截器 - 后续添加 Token 注入
// request.interceptors.request.use((config) => {
//   const token = localStorage.getItem('token')
//   if (token) {
//     config.headers.Authorization = `Bearer ${token}`
//   }
//   return config
// })

// 响应拦截器 - 后续添加错误处理
// request.interceptors.response.use(
//   (response) => response.data,
//   (error) => Promise.reject(error)
// )

export default request
