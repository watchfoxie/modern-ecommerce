import { QueryClient } from '@tanstack/react-query'
import { isAxiosError } from 'axios'

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 5 * 60 * 1000,
      refetchOnWindowFocus: false,
      retry: (failureCount, error) => {
        if (isAxiosError(error) && error.response) {
          const status = error.response.status
          if (status >= 400 && status < 500) return false
        }
        return failureCount < 2
      },
    },
  },
})
