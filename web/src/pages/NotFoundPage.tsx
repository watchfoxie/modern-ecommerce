import { Link } from 'react-router-dom'
import { SearchX } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { EmptyState, PageShell } from '@/components/app/PageState'

export default function NotFoundPage() {
  return (
    <PageShell>
      <EmptyState
        icon={<SearchX />}
        title="Pagină negăsită"
        description="Ruta accesată nu există în aplicația MEc."
        action={
          <Button asChild>
            <Link to="/home">Înapoi la acasă</Link>
          </Button>
        }
      />
    </PageShell>
  )
}
