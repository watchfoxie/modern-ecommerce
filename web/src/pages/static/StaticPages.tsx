import { useState } from 'react'
import { Link } from 'react-router-dom'
import { zodResolver } from '@hookform/resolvers/zod'
import { useForm } from 'react-hook-form'
import { z } from 'zod'
import { CheckCircle2, Clock, Mail, MapPin, MessageCircle, Phone, ShieldCheck, Truck } from 'lucide-react'
import { Accordion, AccordionContent, AccordionItem, AccordionTrigger } from '@/components/ui/accordion'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { AspectRatio } from '@/components/ui/aspect-ratio'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Field, FieldError, FieldLabel } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Separator } from '@/components/ui/separator'
import { Textarea } from '@/components/ui/textarea'
import { PageShell, SectionHeader } from '@/components/app/PageState'
import { sanitizeHtml } from '@/lib/sanitize'

const contactSchema = z.object({
  name: z.string().min(2, 'Numele este obligatoriu'),
  email: z.string().email('Email invalid'),
  subject: z.string().min(2, 'Selectați subiectul'),
  message: z.string().min(10, 'Mesajul trebuie să conțină cel puțin 10 caractere'),
})

function Hero({ image, title, text }: { image: string; title: string; text: string }) {
  return (
    <section className="relative overflow-hidden">
      <AspectRatio ratio={16 / 6} className="min-h-[320px] bg-muted">
        <img src={image} alt="" className="h-full w-full object-cover" />
      </AspectRatio>
      <div className="absolute inset-0 bg-gradient-to-r from-background/90 via-background/50 to-transparent" />
      <div className="absolute inset-x-0 bottom-8 mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <h1 className="max-w-2xl text-4xl font-semibold tracking-tight md:text-5xl">{title}</h1>
        <p className="mt-4 max-w-xl text-muted-foreground">{text}</p>
      </div>
    </section>
  )
}

function ContactForm() {
  const [submitted, setSubmitted] = useState(false)
  const form = useForm<z.infer<typeof contactSchema>>({ resolver: zodResolver(contactSchema), defaultValues: { subject: 'orders' } })
  const submit = form.handleSubmit((values) => {
    sanitizeHtml(values.message)
    setSubmitted(true)
  })

  if (submitted) {
    return (
      <Alert>
        <CheckCircle2 />
        <AlertTitle>Mesaj trimis cu succes</AlertTitle>
        <AlertDescription>Mesajul dvs. a fost preluat. Vă vom contacta în cel mai scurt timp.</AlertDescription>
      </Alert>
    )
  }

  return (
    <form onSubmit={submit} className="space-y-4">
      <Field>
        <FieldLabel>Nume</FieldLabel>
        <Input {...form.register('name')} />
        <FieldError>{form.formState.errors.name?.message}</FieldError>
      </Field>
      <Field>
        <FieldLabel>Email</FieldLabel>
        <Input {...form.register('email')} />
        <FieldError>{form.formState.errors.email?.message}</FieldError>
      </Field>
      <Field>
        <FieldLabel>Subiect</FieldLabel>
        <Select defaultValue="orders" onValueChange={(value) => form.setValue('subject', value)}>
          <SelectTrigger><SelectValue /></SelectTrigger>
          <SelectContent>
            <SelectItem value="orders">Comenzi și livrare</SelectItem>
            <SelectItem value="warranty">Garanție</SelectItem>
            <SelectItem value="account">Cont și plăți</SelectItem>
          </SelectContent>
        </Select>
        <FieldError>{form.formState.errors.subject?.message}</FieldError>
      </Field>
      <Field>
        <FieldLabel>Mesaj</FieldLabel>
        <Textarea {...form.register('message')} />
        <FieldError>{form.formState.errors.message?.message}</FieldError>
      </Field>
      <Button type="submit" className="w-full">Trimite mesajul</Button>
    </form>
  )
}

export function SupportPage() {
  return (
    <div>
      <Hero image="/static/assets/images/prod-images/support/hero.png" title="Cum te putem ajuta?" text="Suport pentru comenzi, livrare, garanție și contul tău MEc." />
      <PageShell>
        <SectionHeader title="Întrebări frecvente" />
        <Accordion type="multiple" className="mb-8">
          {[
            ['orders', 'Cum urmăresc comanda?', 'Istoricul comenzilor este disponibil în cont după autentificare.'],
            ['delivery', 'Cum se face livrarea?', 'Livrăm prin curier după acceptarea și confirmarea comenzii.'],
            ['warranty', 'Ce garanție primesc?', 'Fiecare produs păstrează garanția comercială și documentele aferente.'],
          ].map(([value, title, content]) => (
            <AccordionItem key={value} value={value}>
              <AccordionTrigger>{title}</AccordionTrigger>
              <AccordionContent>{content}</AccordionContent>
            </AccordionItem>
          ))}
        </Accordion>
        <div className="grid gap-4 md:grid-cols-3">
          <Card className="rounded-lg"><CardContent className="pt-4"><MessageCircle className="mb-3 size-5" /><h2 className="font-medium">Chat cu noi</h2><p className="text-sm text-muted-foreground">Disponibil prin formularul de contact de mai jos.</p></CardContent></Card>
          <Card className="rounded-lg"><CardContent className="pt-4"><Mail className="mb-3 size-5" /><a href="mailto:support@mec.md" className="font-medium">support@mec.md</a></CardContent></Card>
          <Card className="rounded-lg"><CardContent className="pt-4"><Phone className="mb-3 size-5" /><a href="tel:+37369000000" className="font-medium">+373 69 000 000</a></CardContent></Card>
        </div>
      </PageShell>
    </div>
  )
}

export function ContactsPage() {
  return (
    <div>
      <Hero image="/static/assets/images/prod-images/contacts/hero.png" title="Contacte MEc" text="Canale directe pentru clienți și parteneri Tocana Group LLC." />
      <PageShell>
        <div className="grid gap-8 lg:grid-cols-2">
          <Card className="rounded-lg">
            <CardHeader><CardTitle>Date contact</CardTitle></CardHeader>
            <CardContent className="space-y-4">
              <p className="flex items-center gap-2"><MapPin className="size-4" /> Chișinău, Republica Moldova</p>
              <p className="flex items-center gap-2"><Phone className="size-4" /> +373 69 000 000</p>
              <p className="flex items-center gap-2"><Mail className="size-4" /> contact@mec.md</p>
              <p className="flex items-center gap-2"><Clock className="size-4" /> Luni-Vineri, 09:00-18:00</p>
              <img src="/static/assets/images/prod-images/contacts/hero.png" alt="" className="rounded-lg object-cover" />
            </CardContent>
          </Card>
          <Card className="rounded-lg">
            <CardHeader><CardTitle>Trimite-ne un mesaj</CardTitle></CardHeader>
            <CardContent><ContactForm /></CardContent>
          </Card>
        </div>
      </PageShell>
    </div>
  )
}

export function AboutPage() {
  return (
    <div>
      <Hero image="/static/assets/images/prod-images/about/hero.png" title="Despre MEc" text="Platformă modernă pentru comercializarea electronicelor premium." />
      <PageShell>
        <div className="grid gap-8 md:grid-cols-2 md:items-center">
          <div>
            <SectionHeader title="Modern Electronics Commerce" description="MEc este o platformă modernă pentru cumpărători care caută produse electronice de calitate." />
            <Button asChild><Link to="/categories/smartphones">Explorează catalogul</Link></Button>
          </div>
          <img src="/static/assets/images/prod-images/about/hero.png" alt="" className="rounded-lg object-cover" />
        </div>
        <Separator className="my-10" />
        <div className="grid gap-4 md:grid-cols-3">
          <Card className="rounded-lg"><CardContent className="pt-4"><ShieldCheck className="mb-3 size-6" /><h2 className="font-medium">Garanție</h2><p className="text-sm text-muted-foreground">Produse cu suport comercial.</p></CardContent></Card>
          <Card className="rounded-lg"><CardContent className="pt-4"><Truck className="mb-3 size-6" /><h2 className="font-medium">Livrare</h2><p className="text-sm text-muted-foreground">Proces de comandă simplu și adrese de livrare salvate.</p></CardContent></Card>
          <Card className="rounded-lg"><CardContent className="pt-4"><CheckCircle2 className="mb-3 size-6" /><h2 className="font-medium">Calitate garantată</h2><p className="text-sm text-muted-foreground">Toate informațiile afișate sunt verificate și actualizate permanent.</p></CardContent></Card>
        </div>
      </PageShell>
    </div>
  )
}
