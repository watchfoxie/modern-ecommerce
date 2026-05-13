import { Link } from 'react-router-dom'
import { Mail, MapPin, Phone, Send, Camera, MessageCircle } from 'lucide-react'

export default function Footer() {
  return (
    <footer className="border-t bg-muted/30">
      <div className="mx-auto grid max-w-7xl gap-8 px-4 py-8 sm:px-6 md:grid-cols-[1.2fr_1fr_1fr] lg:px-8">
        <div className="space-y-3">
          <Link to="/home" className="inline-flex items-center gap-2 font-semibold">
            <img src="/static/assets/icons/prod-icons/favicon.svg" alt="" className="h-7 w-7" />
            Modern Electronics Commerce
          </Link>
          <p className="max-w-sm text-sm text-muted-foreground">
            Platformă Tocana Group LLC pentru smartphone-uri și laptopuri cu livrare în Republica Moldova.
          </p>
        </div>

        <div className="space-y-3 text-sm">
          <h2 className="font-medium">Contact</h2>
          <p className="flex items-center gap-2 text-muted-foreground">
            <MapPin className="size-4" />
            Chișinău, Republica Moldova
          </p>
          <a className="flex items-center gap-2 text-muted-foreground hover:text-foreground" href="tel:+37369000000">
            <Phone className="size-4" />
            +373 69 000 000
          </a>
          <a className="flex items-center gap-2 text-muted-foreground hover:text-foreground" href="mailto:support@mec.md">
            <Mail className="size-4" />
            support@mec.md
          </a>
        </div>

        <div className="space-y-3 text-sm">
          <h2 className="font-medium">Informații</h2>
          <div className="grid gap-2 text-muted-foreground">
            <Link to="/support" className="hover:text-foreground">
              Suport
            </Link>
            <Link to="/contacts" className="hover:text-foreground">
              Contacte
            </Link>
            <Link to="/about" className="hover:text-foreground">
              Despre noi
            </Link>
          </div>
          <div className="flex gap-3 text-muted-foreground">
            <Send className="size-4" aria-label="Telegram" />
            <MessageCircle className="size-4" aria-label="Facebook" />
            <Camera className="size-4" aria-label="Instagram" />
          </div>
        </div>
      </div>
      <div className="border-t py-4 text-center text-xs text-muted-foreground">
        &copy; {new Date().getFullYear()} Tocana Group LLC. Toate drepturile rezervate.
      </div>
    </footer>
  )
}
