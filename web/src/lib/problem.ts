import type { ProblemDetailDto } from '@/contracts/problem-detail'
import { toProblemDetailDto } from '@/contracts/problem-detail'

export function problemTitle(error: unknown, fallback = 'Cererea nu a putut fi procesată') {
  const problem = toProblemDetailDto(error)
  return problem.title || fallback
}

export function problemDetail(error: unknown, fallback = 'Încercați din nou peste câteva momente.') {
  const problem = toProblemDetailDto(error)
  return problem.detail || problem.title || fallback
}

export function fieldErrors(problem?: ProblemDetailDto | null) {
  if (!problem?.errors) {
    return {}
  }

  if (Array.isArray(problem.errors)) {
    return { _global: problem.errors }
  }

  return problem.errors
}
