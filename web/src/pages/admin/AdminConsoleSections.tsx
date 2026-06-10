import type { UseFormReturn } from 'react-hook-form'
import { Pencil, Trash2 } from 'lucide-react'
import { ApiErrorAlert, EmptyState, LoadingRows } from '@/components/app/PageState'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Field, FieldError, FieldLabel } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { NativeSelect, NativeSelectOption } from '@/components/ui/native-select'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { TabsContent } from '@/components/ui/tabs'
import { Textarea } from '@/components/ui/textarea'
import type { CategoryDto } from '@/contracts/category'
import type { PagedResponseDto } from '@/contracts/common'
import type { OrderDto, OrderStatus } from '@/contracts/order'
import type { ProductDto } from '@/contracts/product'
import { assetUrl } from '@/lib/assets'
import { formatDateTime, formatMoney, hasActivePromotion, orderStatusLabel } from '@/lib/format'
import type { CategoryFormInput, CategoryFormValues } from './categoryForm'
import type { ProductFormInput, ProductFormValues } from './productForm'

type RefetchableQuery<T> = {
    isLoading: boolean
    isError: boolean
    isSuccess: boolean
    error: unknown
    data: T | undefined
    refetch: () => void | Promise<unknown>
}

type ProductFormHandle = UseFormReturn<ProductFormInput, undefined, ProductFormValues>
type CategoryFormHandle = UseFormReturn<CategoryFormInput, undefined, CategoryFormValues>

type AdminProductsSectionProps = {
    categoriesQuery: RefetchableQuery<PagedResponseDto<CategoryDto>>
    productsQuery: RefetchableQuery<PagedResponseDto<ProductDto>>
    productCategories: CategoryDto[]
    categoriesBySlug: Map<string, string>
    isDeletingProduct: boolean
    onCreateProduct: () => void
    onEditProduct: (product: ProductDto) => void
    onDeleteProduct: (product: ProductDto) => void
    onPreviousPage: () => void
    onNextPage: () => void
}

type AdminCategoriesSectionProps = {
    categoriesQuery: RefetchableQuery<PagedResponseDto<CategoryDto>>
    categories: CategoryDto[]
    isDeletingCategory: boolean
    onCreateCategory: () => void
    onEditCategory: (category: CategoryDto) => void
    onDeleteCategory: (category: CategoryDto) => void
}

type AdminOrdersSectionProps = {
    ordersQuery: RefetchableQuery<PagedResponseDto<OrderDto>>
    orderStatuses: readonly OrderStatus[]
    orderStatusFilter: string
    pendingStatuses: Record<string, OrderStatus>
    isUpdatingOrderStatus: boolean
    onOrderStatusFilterChange: (status: string) => void
    onPendingStatusChange: (orderId: string, status: OrderStatus) => void
    onUpdateOrderStatus: (orderId: string, status: OrderStatus) => void
    onPreviousPage: () => void
    onNextPage: () => void
}

type ProductDialogProps = {
    open: boolean
    editingProduct: ProductDto | null
    productCategories: CategoryDto[]
    productForm: ProductFormHandle
    isPending: boolean
    onOpenChange: (open: boolean) => void
    onSubmit: (values: ProductFormValues) => void
}

type CategoryDialogProps = {
    open: boolean
    editingCategory: CategoryDto | null
    categories: CategoryDto[]
    categoryForm: CategoryFormHandle
    isPending: boolean
    onOpenChange: (open: boolean) => void
    onSubmit: (values: CategoryFormValues) => void
}

const PRODUCT_TEXT_FIELDS = ['name', 'brand', 'model', 'country', 'currency'] as const
const PRODUCT_NUMBER_FIELDS = ['price', 'promotionalPrice', 'stock'] as const
const CATEGORY_INPUT_FIELDS = ['slug', 'name', 'imageUrl', 'displayOrder'] as const

function FormErrorBanner({ message }: Readonly<{ message?: string }>) {
    if (!message) {
        return null
    }

    return (
        <div className="rounded-lg border border-destructive/30 bg-destructive/5 px-3 py-2 text-sm text-destructive">
            {message}
        </div>
    )
}

function AdminOrderDetails({ order }: Readonly<{ order: OrderDto }>) {
    return (
        <div className="space-y-3 rounded-lg border p-4">
            <div className="grid gap-2 sm:grid-cols-2">
                <div>
                    <p className="text-xs uppercase text-muted-foreground">Comandă</p>
                    <p className="font-mono text-xs">{order.orderNumber}</p>
                </div>
                <div>
                    <p className="text-xs uppercase text-muted-foreground">Utilizator</p>
                    <p className="text-sm">{order.userId}</p>
                </div>
                <div>
                    <p className="text-xs uppercase text-muted-foreground">Plasată</p>
                    <p className="text-sm">{formatDateTime(order.createdAt)}</p>
                </div>
                <div>
                    <p className="text-xs uppercase text-muted-foreground">Livrare</p>
                    <p className="text-sm">{order.deliveryAddress.recipientName} · {order.deliveryAddress.recipientPhone}</p>
                </div>
            </div>
            <Table>
                <TableHeader>
                    <TableRow>
                        <TableHead>Produs</TableHead>
                        <TableHead>Cantitate</TableHead>
                        <TableHead>Preț unitar</TableHead>
                    </TableRow>
                </TableHeader>
                <TableBody>
                    {order.items.map((item) => (
                        <TableRow key={`${order.id}-${item.productId}`}>
                            <TableCell>
                                <div className="flex items-center gap-3">
                                    <img src={assetUrl(item.imageUrl)} alt="" className="h-10 w-10 rounded-md border object-contain p-1" />
                                    <div>
                                        <p className="font-medium">{item.name}</p>
                                        <p className="text-xs text-muted-foreground">{item.brand}</p>
                                    </div>
                                </div>
                            </TableCell>
                            <TableCell>{item.quantity}</TableCell>
                            <TableCell>{formatMoney(item.unitPrice, order.currency)}</TableCell>
                        </TableRow>
                    ))}
                </TableBody>
            </Table>
            <p className="text-sm text-muted-foreground">
                {order.deliveryAddress.street}, {order.deliveryAddress.city}, {order.deliveryAddress.district} {order.deliveryAddress.postalCode ?? ''}
            </p>
            <p className="font-semibold">Total: {formatMoney(order.totalAmount, order.currency)}</p>
        </div>
    )
}

export function AdminProductsSection({
    categoriesQuery,
    productsQuery,
    productCategories,
    categoriesBySlug,
    isDeletingProduct,
    onCreateProduct,
    onEditProduct,
    onDeleteProduct,
    onPreviousPage,
    onNextPage,
}: Readonly<AdminProductsSectionProps>) {
    return (
        <TabsContent value="products" className="space-y-4">
            <Card className="rounded-lg">
                <CardHeader className="flex-row items-center justify-between gap-4">
                    <div>
                        <CardTitle>Catalog produse</CardTitle>
                        <p className="text-sm text-muted-foreground">CRUD complet pentru produsele expuse în storefront.</p>
                    </div>
                    <Button type="button" onClick={onCreateProduct} disabled={productCategories.length === 0}>
                        Adaugă produs
                    </Button>
                </CardHeader>
                <CardContent className="space-y-4">
                    {categoriesQuery.isLoading || productsQuery.isLoading ? <LoadingRows count={5} /> : null}
                    {categoriesQuery.isError ? <ApiErrorAlert error={categoriesQuery.error} onRetry={() => categoriesQuery.refetch()} /> : null}
                    {productsQuery.isError ? <ApiErrorAlert error={productsQuery.error} onRetry={() => productsQuery.refetch()} /> : null}
                    {!categoriesQuery.isLoading && productCategories.length === 0 ? (
                        <EmptyState title="Nu există categorii" description="Creează mai întâi o categorie activă pentru a putea adăuga produse." />
                    ) : null}
                    {productsQuery.data?.data.length ? (
                        <div className="overflow-hidden rounded-lg border">
                            <Table>
                                <TableHeader>
                                    <TableRow>
                                        <TableHead>Produs</TableHead>
                                        <TableHead>Categorie</TableHead>
                                        <TableHead>Preț</TableHead>
                                        <TableHead>Stoc</TableHead>
                                        <TableHead>Status</TableHead>
                                        <TableHead className="text-right">Acțiuni</TableHead>
                                    </TableRow>
                                </TableHeader>
                                <TableBody>
                                    {productsQuery.data.data.map((product) => (
                                        <TableRow key={product.id}>
                                            <TableCell>
                                                <div className="flex items-center gap-3">
                                                    <img src={assetUrl(product.imageUrls[0])} alt="" className="h-12 w-12 rounded-md border object-contain p-1" />
                                                    <div>
                                                        <p className="font-medium">{product.name}</p>
                                                        <p className="text-xs text-muted-foreground">{product.brand} · {product.model}</p>
                                                    </div>
                                                </div>
                                            </TableCell>
                                            <TableCell>{categoriesBySlug.get(product.categorySlug) ?? product.categorySlug}</TableCell>
                                            <TableCell>
                                                <div>
                                                    <p>{formatMoney(hasActivePromotion(product.price, product.promotionalPrice ?? null) ? product.promotionalPrice : product.price, product.currency)}</p>
                                                    {hasActivePromotion(product.price, product.promotionalPrice ?? null) ? <p className="text-xs text-muted-foreground line-through">{formatMoney(product.price, product.currency)}</p> : null}
                                                </div>
                                            </TableCell>
                                            <TableCell>{product.stock}</TableCell>
                                            <TableCell>
                                                <Badge variant={product.isActive ? 'secondary' : 'outline'}>{product.isActive ? 'Activ' : 'Inactiv'}</Badge>
                                            </TableCell>
                                            <TableCell className="text-right">
                                                <div className="flex justify-end gap-2">
                                                    <Button type="button" variant="ghost" size="icon" onClick={() => onEditProduct(product)}>
                                                        <Pencil />
                                                        <span className="sr-only">Editează produs</span>
                                                    </Button>
                                                    <Button
                                                        type="button"
                                                        variant="ghost"
                                                        size="icon"
                                                        disabled={isDeletingProduct}
                                                        onClick={() => onDeleteProduct(product)}
                                                    >
                                                        <Trash2 />
                                                        <span className="sr-only">Șterge produs</span>
                                                    </Button>
                                                </div>
                                            </TableCell>
                                        </TableRow>
                                    ))}
                                </TableBody>
                            </Table>
                        </div>
                    ) : null}
                    {productsQuery.isSuccess && productsQuery.data?.data.length === 0 ? (
                        <EmptyState title="Nu există produse" description="Catalogul administrativ este gol în acest moment." />
                    ) : null}
                    {productsQuery.data && productsQuery.data.totalPages > 1 ? (
                        <div className="flex justify-center gap-2">
                            <Button variant="outline" disabled={productsQuery.data.first} onClick={onPreviousPage}>Anterior</Button>
                            <Button variant="outline" disabled={productsQuery.data.last} onClick={onNextPage}>Următor</Button>
                        </div>
                    ) : null}
                </CardContent>
            </Card>
        </TabsContent>
    )
}

export function AdminCategoriesSection({
    categoriesQuery,
    categories,
    isDeletingCategory,
    onCreateCategory,
    onEditCategory,
    onDeleteCategory,
}: Readonly<AdminCategoriesSectionProps>) {
    return (
        <TabsContent value="categories" className="space-y-4">
            <Card className="rounded-lg">
                <CardHeader className="flex-row items-center justify-between gap-4">
                    <div>
                        <CardTitle>Categorii</CardTitle>
                        <p className="text-sm text-muted-foreground">CRUD complet pentru taxonomia consumată de catalog.</p>
                    </div>
                    <Button type="button" onClick={onCreateCategory}>
                        Adaugă categorie
                    </Button>
                </CardHeader>
                <CardContent className="space-y-4">
                    {categoriesQuery.isLoading ? <LoadingRows count={4} /> : null}
                    {categoriesQuery.isError ? <ApiErrorAlert error={categoriesQuery.error} onRetry={() => categoriesQuery.refetch()} /> : null}
                    {categoriesQuery.data?.data.length ? (
                        <div className="overflow-hidden rounded-lg border">
                            <Table>
                                <TableHeader>
                                    <TableRow>
                                        <TableHead>Nume</TableHead>
                                        <TableHead>Slug</TableHead>
                                        <TableHead>Părinte</TableHead>
                                        <TableHead>Ordine</TableHead>
                                        <TableHead>Status</TableHead>
                                        <TableHead className="text-right">Acțiuni</TableHead>
                                    </TableRow>
                                </TableHeader>
                                <TableBody>
                                    {categoriesQuery.data.data.map((category) => (
                                        <TableRow key={category.id}>
                                            <TableCell>
                                                <div>
                                                    <p className="font-medium">{category.name}</p>
                                                    <p className="text-xs text-muted-foreground">{category.description ?? 'Fără descriere'}</p>
                                                </div>
                                            </TableCell>
                                            <TableCell className="font-mono text-xs">{category.slug}</TableCell>
                                            <TableCell>{categories.find((item) => item.id === category.parentId)?.name ?? 'Rădăcină'}</TableCell>
                                            <TableCell>{category.displayOrder}</TableCell>
                                            <TableCell>
                                                <Badge variant={category.isActive ? 'secondary' : 'outline'}>{category.isActive ? 'Activă' : 'Inactivă'}</Badge>
                                            </TableCell>
                                            <TableCell className="text-right">
                                                <div className="flex justify-end gap-2">
                                                    <Button type="button" variant="ghost" size="icon" onClick={() => onEditCategory(category)}>
                                                        <Pencil />
                                                        <span className="sr-only">Editează categoria</span>
                                                    </Button>
                                                    <Button
                                                        type="button"
                                                        variant="ghost"
                                                        size="icon"
                                                        disabled={isDeletingCategory}
                                                        onClick={() => onDeleteCategory(category)}
                                                    >
                                                        <Trash2 />
                                                        <span className="sr-only">Șterge categoria</span>
                                                    </Button>
                                                </div>
                                            </TableCell>
                                        </TableRow>
                                    ))}
                                </TableBody>
                            </Table>
                        </div>
                    ) : null}
                    {categoriesQuery.isSuccess && categoriesQuery.data?.data.length === 0 ? (
                        <EmptyState title="Nu există categorii" description="Catalogul administrativ nu are încă taxonomii configurate." />
                    ) : null}
                </CardContent>
            </Card>
        </TabsContent>
    )
}

export function AdminOrdersSection({
    ordersQuery,
    orderStatuses,
    orderStatusFilter,
    pendingStatuses,
    isUpdatingOrderStatus,
    onOrderStatusFilterChange,
    onPendingStatusChange,
    onUpdateOrderStatus,
    onPreviousPage,
    onNextPage,
}: Readonly<AdminOrdersSectionProps>) {
    return (
        <TabsContent value="orders" className="space-y-4">
            <Card className="rounded-lg">
                <CardHeader className="flex-row items-center justify-between gap-4">
                    <div>
                        <CardTitle>Administrare comenzi</CardTitle>
                        <p className="text-sm text-muted-foreground">Listare completă și actualizare de status pentru toate comenzile.</p>
                    </div>
                    <div className="w-full max-w-xs">
                        <NativeSelect value={orderStatusFilter} onChange={(event) => onOrderStatusFilterChange(event.target.value)} className="w-full">
                            <NativeSelectOption value="">Toate statusurile</NativeSelectOption>
                            {orderStatuses.map((status) => (
                                <NativeSelectOption key={status} value={status}>{orderStatusLabel(status)}</NativeSelectOption>
                            ))}
                        </NativeSelect>
                    </div>
                </CardHeader>
                <CardContent className="space-y-4">
                    {ordersQuery.isLoading ? <LoadingRows count={5} /> : null}
                    {ordersQuery.isError ? <ApiErrorAlert error={ordersQuery.error} onRetry={() => ordersQuery.refetch()} /> : null}
                    {ordersQuery.data?.data.length ? (
                        <div className="space-y-4">
                            <div className="overflow-hidden rounded-lg border">
                                <Table>
                                    <TableHeader>
                                        <TableRow>
                                            <TableHead>Număr</TableHead>
                                            <TableHead>Utilizator</TableHead>
                                            <TableHead>Data</TableHead>
                                            <TableHead>Total</TableHead>
                                            <TableHead>Status</TableHead>
                                            <TableHead className="text-right">Actualizare</TableHead>
                                        </TableRow>
                                    </TableHeader>
                                    <TableBody>
                                        {ordersQuery.data.data.map((order) => {
                                            const selectedStatus = pendingStatuses[order.id] ?? order.status

                                            return (
                                                <TableRow key={order.id}>
                                                    <TableCell className="font-mono text-xs">{order.orderNumber}</TableCell>
                                                    <TableCell>{order.userId}</TableCell>
                                                    <TableCell>{formatDateTime(order.createdAt)}</TableCell>
                                                    <TableCell>{formatMoney(order.totalAmount, order.currency)}</TableCell>
                                                    <TableCell>
                                                        <Badge variant="secondary">{orderStatusLabel(order.status)}</Badge>
                                                    </TableCell>
                                                    <TableCell className="text-right">
                                                        <div className="flex flex-col items-end gap-2 sm:flex-row sm:justify-end">
                                                            <NativeSelect value={selectedStatus} onChange={(event) => onPendingStatusChange(order.id, event.target.value as OrderStatus)}>
                                                                {orderStatuses.map((status) => (
                                                                    <NativeSelectOption key={status} value={status}>{orderStatusLabel(status)}</NativeSelectOption>
                                                                ))}
                                                            </NativeSelect>
                                                            <Button
                                                                type="button"
                                                                variant="outline"
                                                                disabled={selectedStatus === order.status || isUpdatingOrderStatus}
                                                                onClick={() => onUpdateOrderStatus(order.id, selectedStatus)}
                                                            >
                                                                Actualizează
                                                            </Button>
                                                        </div>
                                                    </TableCell>
                                                </TableRow>
                                            )
                                        })}
                                    </TableBody>
                                </Table>
                            </div>

                            {ordersQuery.data.data.map((order) => (
                                <AdminOrderDetails key={`details-${order.id}`} order={order} />
                            ))}
                        </div>
                    ) : null}
                    {ordersQuery.isSuccess && ordersQuery.data?.data.length === 0 ? (
                        <EmptyState title="Nu există comenzi pentru filtrul curent" description="Schimbă filtrul sau revino după plasarea altor comenzi." />
                    ) : null}
                    {ordersQuery.data && ordersQuery.data.totalPages > 1 ? (
                        <div className="flex justify-center gap-2">
                            <Button variant="outline" disabled={ordersQuery.data.first} onClick={onPreviousPage}>Anterior</Button>
                            <Button variant="outline" disabled={ordersQuery.data.last} onClick={onNextPage}>Următor</Button>
                        </div>
                    ) : null}
                </CardContent>
            </Card>
        </TabsContent>
    )
}

export function ProductDialog({
    open,
    editingProduct,
    productCategories,
    productForm,
    isPending,
    onOpenChange,
    onSubmit,
}: Readonly<ProductDialogProps>) {
    return (
        <Dialog open={open} onOpenChange={onOpenChange}>
            <DialogContent className="max-w-3xl">
                <DialogHeader>
                    <DialogTitle>{editingProduct ? 'Editează produsul' : 'Produs nou'}</DialogTitle>
                    <DialogDescription>
                        Configurează datele expuse în catalog pentru produsul selectat.
                    </DialogDescription>
                </DialogHeader>
                <form onSubmit={productForm.handleSubmit(onSubmit)} className="grid gap-4 md:grid-cols-2">
                    <div className="md:col-span-2">
                        <FormErrorBanner message={productForm.formState.errors.root?.serverError?.message} />
                    </div>
                    <Field>
                        <FieldLabel>Categorie</FieldLabel>
                        <NativeSelect className="w-full" {...productForm.register('categoryId')}>
                            <NativeSelectOption value="">Selectează categoria</NativeSelectOption>
                            {productCategories.map((category) => (
                                <NativeSelectOption key={category.id} value={category.id}>{category.name}</NativeSelectOption>
                            ))}
                        </NativeSelect>
                        <FieldError>{productForm.formState.errors.categoryId?.message}</FieldError>
                    </Field>
                    <Field>
                        <FieldLabel>Slug</FieldLabel>
                        <Input {...productForm.register('slug')} />
                        <FieldError>{productForm.formState.errors.slug?.message}</FieldError>
                    </Field>
                    {PRODUCT_TEXT_FIELDS.map((name) => (
                        <Field key={name}>
                            <FieldLabel>{name}</FieldLabel>
                            <Input {...productForm.register(name)} />
                            <FieldError>{productForm.formState.errors[name]?.message}</FieldError>
                        </Field>
                    ))}
                    {PRODUCT_NUMBER_FIELDS.map((name) => (
                        <Field key={name}>
                            <FieldLabel>{name}</FieldLabel>
                            <Input type="number" step={name === 'stock' ? '1' : '0.01'} {...productForm.register(name)} />
                            <FieldError>{productForm.formState.errors[name]?.message}</FieldError>
                        </Field>
                    ))}
                    <Field className="md:col-span-2">
                        <FieldLabel>Imagini</FieldLabel>
                        <Textarea rows={4} placeholder="Un URL pe linie" {...productForm.register('imageUrlsText')} />
                        <FieldError>{productForm.formState.errors.imageUrlsText?.message}</FieldError>
                    </Field>
                    <Field className="md:col-span-2">
                        <FieldLabel>Specificații</FieldLabel>
                        <Textarea rows={5} placeholder="cheie: valoare" {...productForm.register('specsText')} />
                        <FieldError>{productForm.formState.errors.specsText?.message}</FieldError>
                    </Field>
                    <label className="md:col-span-2 flex items-center gap-2 text-sm">
                        <input type="checkbox" {...productForm.register('isActive')} />
                        <span>Produs activ în catalog</span>
                    </label>
                    <div className="md:col-span-2 flex justify-end gap-2">
                        <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>Renunță</Button>
                        <Button type="submit" disabled={isPending}>{editingProduct ? 'Salvează' : 'Creează'}</Button>
                    </div>
                </form>
            </DialogContent>
        </Dialog>
    )
}

export function CategoryDialog({
    open,
    editingCategory,
    categories,
    categoryForm,
    isPending,
    onOpenChange,
    onSubmit,
}: Readonly<CategoryDialogProps>) {
    return (
        <Dialog open={open} onOpenChange={onOpenChange}>
            <DialogContent className="max-w-2xl">
                <DialogHeader>
                    <DialogTitle>{editingCategory ? 'Editează categoria' : 'Categorie nouă'}</DialogTitle>
                    <DialogDescription>
                        Actualizează taxonomia utilizată de catalog și de formularele administrative.
                    </DialogDescription>
                </DialogHeader>
                <form onSubmit={categoryForm.handleSubmit(onSubmit)} className="grid gap-4 md:grid-cols-2">
                    <div className="md:col-span-2">
                        <FormErrorBanner message={categoryForm.formState.errors.root?.serverError?.message} />
                    </div>
                    {CATEGORY_INPUT_FIELDS.map((name) => (
                        <Field key={name}>
                            <FieldLabel>{name}</FieldLabel>
                            <Input type={name === 'displayOrder' ? 'number' : 'text'} {...categoryForm.register(name)} />
                            <FieldError>{categoryForm.formState.errors[name]?.message}</FieldError>
                        </Field>
                    ))}
                    <Field>
                        <FieldLabel>Părinte</FieldLabel>
                        <NativeSelect className="w-full" {...categoryForm.register('parentId')}>
                            <NativeSelectOption value="">Fără categorie părinte</NativeSelectOption>
                            {categories
                                .filter((category) => category.id !== editingCategory?.id)
                                .map((category) => (
                                    <NativeSelectOption key={category.id} value={category.id}>{category.name}</NativeSelectOption>
                                ))}
                        </NativeSelect>
                        <FieldError>{categoryForm.formState.errors.parentId?.message}</FieldError>
                    </Field>
                    <Field className="md:col-span-2">
                        <FieldLabel>Descriere</FieldLabel>
                        <Textarea rows={4} {...categoryForm.register('description')} />
                        <FieldError>{categoryForm.formState.errors.description?.message}</FieldError>
                    </Field>
                    <label className="md:col-span-2 flex items-center gap-2 text-sm">
                        <input type="checkbox" {...categoryForm.register('isActive')} />
                        <span>Categorie activă</span>
                    </label>
                    <div className="md:col-span-2 flex justify-end gap-2">
                        <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>Renunță</Button>
                        <Button type="submit" disabled={isPending}>{editingCategory ? 'Salvează' : 'Creează'}</Button>
                    </div>
                </form>
            </DialogContent>
        </Dialog>
    )
}