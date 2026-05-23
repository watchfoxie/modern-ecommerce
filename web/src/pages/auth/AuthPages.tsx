import { useState } from 'react'
import { Link, useLocation, useNavigate, useSearchParams } from 'react-router-dom'
import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation } from '@tanstack/react-query'
import { useForm, type FieldValues, type Path, type UseFormRegister } from 'react-hook-form'
import { z } from 'zod'
import { Eye, EyeOff, LockKeyhole } from 'lucide-react'
import { toast } from 'sonner'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Field, FieldError, FieldLabel } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { Separator } from '@/components/ui/separator'
import { ApiErrorAlert, PageShell } from '@/components/app/PageState'
import { authService } from '@/contracts/auth'
import { useAuthStore } from '@/stores/authStore'

const signUpSchema = z.object({
  firstName: z.string().min(2, 'Prenumele este obligatoriu'),
  lastName: z.string().min(2, 'Numele este obligatoriu'),
  email: z.string().email('Email invalid'),
  password: z.string().min(8, 'Parola trebuie să aibă cel puțin 8 caractere'),
})

const signInSchema = z.object({
  email: z.string().email('Email invalid'),
  password: z.string().min(1, 'Parola este obligatorie'),
})

const resetRequestSchema = z.object({
  email: z.string().email('Email invalid'),
})

const resetConfirmSchema = z.object({
  token: z.string().min(1, 'Tokenul este obligatoriu'),
  newPassword: z.string().min(8, 'Parola trebuie să aibă cel puțin 8 caractere'),
  confirmPassword: z.string().min(8),
}).refine((value) => value.newPassword === value.confirmPassword, {
  path: ['confirmPassword'],
  message: 'Parolele nu coincid',
})

function PasswordInput<T extends FieldValues>({ register, name }: { register: UseFormRegister<T>; name: Path<T> }) {
  const [visible, setVisible] = useState(false)
  return (
    <div className="relative">
      <Input type={visible ? 'text' : 'password'} {...register(name)} className="pr-10" />
      <Button type="button" variant="ghost" size="icon-sm" className="absolute right-1 top-1/2 -translate-y-1/2" onClick={() => setVisible((value) => !value)}>
        {visible ? <EyeOff /> : <Eye />}
        <span className="sr-only">Afișează parola</span>
      </Button>
    </div>
  )
}

export function SignUpPage() {
  const navigate = useNavigate()
  const form = useForm<z.infer<typeof signUpSchema>>({ resolver: zodResolver(signUpSchema) })
  const mutation = useMutation({
    mutationFn: authService.signUp,
    onSuccess: () => {
      toast.success('Cont creat. Autentificați-vă pentru a continua.')
      navigate('/profile/sign-in')
    },
  })

  return (
    <PageShell>
      <Card className="mx-auto max-w-md rounded-lg">
        <CardHeader>
          <CardTitle>Înregistrare</CardTitle>
          <CardDescription>Creați o identitate MEc pentru coș și comenzi.</CardDescription>
        </CardHeader>
        <CardContent>
          {mutation.isError && <ApiErrorAlert error={mutation.error} />}
          <form onSubmit={form.handleSubmit((values) => mutation.mutate(values))} className="mt-4 space-y-4">
            {(['firstName', 'lastName', 'email'] as const).map((name) => (
              <Field key={name}>
                <FieldLabel htmlFor={name}>{name}</FieldLabel>
                <Input id={name} {...form.register(name)} aria-invalid={Boolean(form.formState.errors[name])} />
                <FieldError>{form.formState.errors[name]?.message}</FieldError>
              </Field>
            ))}
            <Field>
              <FieldLabel>Parolă</FieldLabel>
              <PasswordInput register={form.register} name="password" />
              <FieldError>{form.formState.errors.password?.message}</FieldError>
            </Field>
            <Button type="submit" className="w-full" disabled={mutation.isPending}>Creează cont</Button>
          </form>
          <Separator className="my-4" />
          <Button asChild variant="link" className="w-full">
            <Link to="/profile/sign-in">Ai deja cont? Autentifică-te</Link>
          </Button>
        </CardContent>
      </Card>
    </PageShell>
  )
}

export function SignInPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const setAuth = useAuthStore((state) => state.setAuth)
  const form = useForm<z.infer<typeof signInSchema>>({ resolver: zodResolver(signInSchema) })
  const mutation = useMutation({
    mutationFn: authService.signIn,
    onSuccess: (response) => {
      const redirectTo = (location.state as { redirectTo?: string } | null)?.redirectTo ?? '/home'
      setAuth(response)
      navigate(redirectTo, { replace: true, flushSync: true })
    },
  })

  return (
    <PageShell>
      <Card className="mx-auto max-w-md rounded-lg">
        <CardHeader>
          <CardTitle>Autentificare</CardTitle>
          <CardDescription>Token-ul JWT va fi atașat centralizat de Axios.</CardDescription>
        </CardHeader>
        <CardContent>
          {mutation.isError && (
            <Alert variant="destructive">
              <LockKeyhole />
              <AlertTitle>Autentificare eșuată</AlertTitle>
              <AlertDescription>Email sau parolă incorecte.</AlertDescription>
            </Alert>
          )}
          <form onSubmit={form.handleSubmit((values) => mutation.mutate(values))} className="mt-4 space-y-4">
            <Field>
              <FieldLabel>Email</FieldLabel>
              <Input {...form.register('email')} aria-invalid={Boolean(form.formState.errors.email)} />
              <FieldError>{form.formState.errors.email?.message}</FieldError>
            </Field>
            <Field>
              <div className="flex items-center justify-between">
                <FieldLabel>Parolă</FieldLabel>
                <Link to="/profile/password-reset" className="text-xs text-primary hover:underline">Ai uitat parola?</Link>
              </div>
              <PasswordInput register={form.register} name="password" />
              <FieldError>{form.formState.errors.password?.message}</FieldError>
            </Field>
            <Button type="submit" className="w-full" disabled={mutation.isPending}>Autentifică-te</Button>
          </form>
          <Separator className="my-4" />
          <Button asChild variant="link" className="w-full">
            <Link to="/profile/sign-up">Nu ai cont? Înregistrează-te</Link>
          </Button>
        </CardContent>
      </Card>
    </PageShell>
  )
}

export function PasswordResetPage() {
  const [params] = useSearchParams()
  const [step, setStep] = useState<'request' | 'confirm'>(params.get('token') ? 'confirm' : 'request')
  const navigate = useNavigate()
  const requestForm = useForm<z.infer<typeof resetRequestSchema>>({ resolver: zodResolver(resetRequestSchema) })
  const confirmForm = useForm<z.infer<typeof resetConfirmSchema>>({
    resolver: zodResolver(resetConfirmSchema),
    defaultValues: { token: params.get('token') ?? '', newPassword: '', confirmPassword: '' },
  })
  const requestMutation = useMutation({
    mutationFn: authService.requestPasswordReset,
    onSettled: () => setStep('confirm'),
  })
  const confirmMutation = useMutation({
    mutationFn: ({ token, newPassword }: z.infer<typeof resetConfirmSchema>) => authService.confirmPasswordReset({ token, newPassword }),
    onSuccess: () => {
      toast.success('Parola a fost resetată')
      navigate('/profile/sign-in')
    },
  })

  return (
    <PageShell>
      <Card className="mx-auto max-w-md rounded-lg">
        <CardHeader>
          <CardTitle>Resetare parolă</CardTitle>
          <CardDescription>Fluxul nu dezvăluie dacă adresa există în sistem.</CardDescription>
        </CardHeader>
        <CardContent>
          {step === 'request' ? (
            <form onSubmit={requestForm.handleSubmit((values) => requestMutation.mutate(values))} className="space-y-4">
              <Field>
                <FieldLabel>Email</FieldLabel>
                <Input {...requestForm.register('email')} />
                <FieldError>{requestForm.formState.errors.email?.message}</FieldError>
              </Field>
              <Button type="submit" className="w-full" disabled={requestMutation.isPending}>Solicită resetare</Button>
            </form>
          ) : (
            <form onSubmit={confirmForm.handleSubmit((values) => confirmMutation.mutate(values))} className="space-y-4">
              <Alert>
                <AlertTitle>Verificați emailul</AlertTitle>
                <AlertDescription>Dacă adresa există, veți primi un token de resetare.</AlertDescription>
              </Alert>
              {confirmMutation.isError && <ApiErrorAlert error={confirmMutation.error} />}
              <Field>
                <FieldLabel>Token</FieldLabel>
                <Input {...confirmForm.register('token')} />
                <FieldError>{confirmForm.formState.errors.token?.message}</FieldError>
              </Field>
              <Field>
                <FieldLabel>Parolă nouă</FieldLabel>
                <PasswordInput register={confirmForm.register} name="newPassword" />
                <FieldError>{confirmForm.formState.errors.newPassword?.message}</FieldError>
              </Field>
              <Field>
                <FieldLabel>Confirmă parola</FieldLabel>
                <PasswordInput register={confirmForm.register} name="confirmPassword" />
                <FieldError>{confirmForm.formState.errors.confirmPassword?.message}</FieldError>
              </Field>
              <Button type="submit" className="w-full" disabled={confirmMutation.isPending}>Resetează parola</Button>
            </form>
          )}
        </CardContent>
      </Card>
    </PageShell>
  )
}
