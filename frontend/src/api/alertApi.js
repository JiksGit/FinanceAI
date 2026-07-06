import api from './axios'

export const getAlerts = () => api.get('/alerts').then((r) => r.data)
export const getUnreadAlerts = () => api.get('/alerts/unread').then((r) => r.data)
export const getUnreadCount = () => api.get('/alerts/count').then((r) => r.data.count)
export const markRead = (id) => api.put(`/alerts/${id}/read`)
export const markAllRead = () => api.put('/alerts/read-all')
export const setTargetPrice = (stockCode, targetPrice, targetAbove) =>
  api.put(`/alerts/target/${stockCode}`, { targetPrice, targetAbove })
export const clearTargetPrice = (stockCode) => api.delete(`/alerts/target/${stockCode}`)
