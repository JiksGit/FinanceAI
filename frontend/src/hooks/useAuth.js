import { useAuthStore } from '../store/authStore'

export function useAuth() {
  const accessToken = useAuthStore((state) => state.accessToken)
  const nickname = useAuthStore((state) => state.nickname)
  const login = useAuthStore((state) => state.login)
  const logout = useAuthStore((state) => state.logout)

  return {
    isAuthenticated: !!accessToken,
    nickname,
    login,
    logout,
  }
}
