import { useEffect, useMemo, useState } from 'react'
import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'
import { z } from 'zod'
import { FolderTree, Package, Pencil, ShieldCheck, ShoppingBag, Trash2 } from 'lucide-react'
import { toast } from 'sonner'
import { ApiErrorAlert, EmptyState, LoadingRows, SectionHeader } from '@/components/app/PageState'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Field, FieldError, FieldLabel } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { NativeSelect, NativeSelectOption } from '@/components/ui/native-select'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { Textarea } from '@/components/ui/textarea'
import { categoryService, type CategoryDto } from '@/contracts/category'
import type { PagedResponseDto } from '@/contracts/common'
import { orderService, type OrderDto, type OrderStatus } from '@/contracts/order'
import { productService, type ProductDto } from '@/contracts/product'
import { assetUrl } from '@/lib/assets'
import { formatDateTime, formatMoney, hasActivePromotion, orderStatusLabel } from '@/lib/format'
import { problemDetail } from '@/lib/problem'
import {
    buildProductUpsertRequest,
    emptyProductFormValues,
    getSelectableProductCategories,
    productFormSchema,
    productToFormValues,
    type ProductFormInput,
    type ProductFormValues,
} from './productForm'

const categoryFormSchema = z.object({
    slug: z.string().min(2, 'Slug-ul trebuie să aibă cel puțin 2 caractere.'),
    name: z.string().min(2, 'Numele trebuie să aibă cel puțin 2 caractere.'),
    description: z.string().optional(),
    parentId: z.string().optional(),
    imageUrl: z.string().optional(),
    displayOrder: z.coerce.number().int().min(0, 'Ordinea nu poate fi negativă.'),
    isActive: z.boolean(),
})

const orderStatuses: OrderStatus[] = ['CREATED', 'CONFIRMED', 'PROCESSING', 'SHIPPED', 'DELIVERED', 'CANCELLED']

type CategoryFormInput = z.input<typeof categoryFormSchema>
type CategoryFormValues = z.output<typeof categoryFormSchema>

function categoryToFormValues(category: CategoryDto) {
    return {
        slug: category.slug,
        name: category.name,
        description: category.description ?? '',
        parentId: category.parentId ?? '',
        imageUrl: category.imageUrl ?? '',
        displayOrder: category.displayOrder,
        isActive: category.isActive,
    }
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

export default function AdminConsolePage() {
    const queryClient = useQueryClient()
    const [productPage, setProductPage] = useState(0)
    const [orderPage, setOrderPage] = useState(0)
    const [orderStatusFilter, setOrderStatusFilter] = useState<string>('')
    const [editingProduct, setEditingProduct] = useState<ProductDto | null>(null)
    const [productDialogOpen, setProductDialogOpen] = useState(false)
    const [editingCategory, setEditingCategory] = useState<CategoryDto | null>(null)
    const [categoryDialogOpen, setCategoryDialogOpen] = useState(false)
    const [pendingStatuses, setPendingStatuses] = useState<Record<string, OrderStatus>>({})

    const categoriesQuery = useQuery({
        queryKey: ['admin-categories'],
        queryFn: () => categoryService.list({ page: 0, size: 100 }),
    })
    const productsQuery = useQuery({
        queryKey: ['admin-products', productPage],
        queryFn: () => productService.list({ page: productPage, size: 10, sort: 'createdAt', direction: 'desc' }),
    })
    const ordersQuery = useQuery({
        queryKey: ['admin-orders', orderPage, orderStatusFilter],
        queryFn: () => orderService.listAll({ page: orderPage, size: 10, status: orderStatusFilter || undefined }),
    })

    const categories = categoriesQuery.data?.data ?? []
    const productCategories = useMemo(() => getSelectableProductCategories(categories), [categories])
    const productForm = useForm<ProductFormInput, undefined, ProductFormValues>({
        resolver: zodResolver(productFormSchema),
        defaultValues: emptyProductFormValues(),
    })
    const categoryForm = useForm<CategoryFormInput, undefined, CategoryFormValues>({
        resolver: zodResolver(categoryFormSchema),
        defaultValues: {
            slug: '',
            name: '',
            description: '',
            parentId: '',
            imageUrl: '',
            displayOrder: 0,
            isActive: true,
        },
    })

    const categoriesBySlug = useMemo(
        () => new Map(categories.map((category) => [category.slug, category.name] as const)),
        [categories],
    )

    useEffect(() => {
        if (!productDialogOpen) {
            return
        }

        if (editingProduct) {
            productForm.reset(productToFormValues(editingProduct, categories))
            return
        }

        productForm.reset(emptyProductFormValues(productCategories[0]?.id ?? ''))
    }, [categories, editingProduct, productCategories, productDialogOpen, productForm])

    useEffect(() => {
        if (!categoryDialogOpen) {
            return
        }

        if (editingCategory) {
            categoryForm.reset(categoryToFormValues(editingCategory))
            return
        }

        categoryForm.reset({
            slug: '',
            name: '',
            description: '',
            parentId: '',
            imageUrl: '',
            displayOrder: 0,
            isActive: true,
        })
    }, [categoryDialogOpen, categoryForm, editingCategory])

    const upsertProduct = useMutation({
        mutationFn: async (values: ProductFormValues) => {
            const payload = buildProductUpsertRequest(values, productCategories)
            return editingProduct
                ? productService.update(editingProduct.slug, payload)
                : productService.create(payload)
        },
        onSuccess: () => {
            setProductDialogOpen(false)
            setEditingProduct(null)
            productForm.reset(emptyProductFormValues(productCategories[0]?.id ?? ''))
            queryClient.invalidateQueries({ queryKey: ['admin-products'] })
            toast.success(editingProduct ? 'Produs actualizat' : 'Produs creat')
        },
        onError: (error) => {
            const detail = problemDetail(error, 'Operațiunea pe produs a eșuat.')
            productForm.setError('root.serverError', { message: detail })
            toast.error(detail)
        },
    })

    const deleteProduct = useMutation({
        mutationFn: (slug: string) => productService.delete(slug),
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['admin-products'] })
            toast.success('Produs șters')
        },
    })

    const upsertCategory = useMutation({
        mutationFn: (values: CategoryFormValues) => {
            const payload = {
                slug: values.slug,
                name: values.name,
                description: values.description || undefined,
                parentId: values.parentId || null,
                imageUrl: values.imageUrl || undefined,
                displayOrder: values.displayOrder,
                isActive: values.isActive,
            }

            return editingCategory
                ? categoryService.update(editingCategory.slug, payload)
                : categoryService.create(payload)
        },
        onSuccess: () => {
            setCategoryDialogOpen(false)
            setEditingCategory(null)
            queryClient.invalidateQueries({ queryKey: ['admin-categories'] })
            toast.success(editingCategory ? 'Categorie actualizată' : 'Categorie creată')
        },
    })

    const deleteCategory = useMutation({
        mutationFn: (slug: string) => categoryService.delete(slug),
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['admin-categories'] })
            toast.success('Categorie ștearsă')
        },
    })

    const updateOrderStatus = useMutation({
        mutationFn: ({ orderId, status }: { orderId: string; status: OrderStatus }) =>
            orderService.updateStatus(orderId, { status }),
        onSuccess: (updatedOrder) => {
            queryClient.setQueriesData<PagedResponseDto<OrderDto>>({ queryKey: ['admin-orders'] }, (current) => {
                if (!current) {
                    return current
                }

                return {
                    ...current,
                    data: current.data.map((order) => (order.id === updatedOrder.id ? updatedOrder : order)),
                }
            })
            setPendingStatuses((current) => {
                const next = { ...current }
                delete next[updatedOrder.id]
                return next
            })
            queryClient.invalidateQueries({ queryKey: ['admin-orders'] })
            toast.success('Statusul comenzii a fost actualizat')
        },
    })

    return (
        <div className="space-y-6">
            <SectionHeader
                title="Consolă administrare"
                description="Operațiuni administrative protejate prin `ROLE_ADMIN` pentru catalog și comenzi."
            />

            <Tabs defaultValue="products" className="gap-4">
                <TabsList>
                    <TabsTrigger value="products"><Package />Produse</TabsTrigger>
                    <TabsTrigger value="categories"><FolderTree />Categorii</TabsTrigger>
                    <TabsTrigger value="orders"><ShoppingBag />Comenzi</TabsTrigger>
                </TabsList>

                <TabsContent value="products" className="space-y-4">
                    <Card className="rounded-lg">
                        <CardHeader className="flex-row items-center justify-between gap-4">
                            <div>
                                <CardTitle>Catalog produse</CardTitle>
                                <p className="text-sm text-muted-foreground">CRUD complet pentru produsele expuse în storefront.</p>
                            </div>
                            <Button
                                type="button"
                                onClick={() => {
                                    setEditingProduct(null)
                                    setProductDialogOpen(true)
                                }}
                                disabled={productCategories.length === 0}
                            >
                                Adaugă produs
                            </Button>
                        </CardHeader>
                        <CardContent className="space-y-4">
                            {categoriesQuery.isLoading || productsQuery.isLoading ? <LoadingRows count={5} /> : null}
                            {categoriesQuery.isError ? <ApiErrorAlert error={categoriesQuery.error} onRetry={() => categoriesQuery.refetch()} /> : null}
                            {productsQuery.isError ? <ApiErrorAlert error={productsQuery.error} onRetry={() => productsQuery.refetch()} /> : null}
                            {!categoriesQuery.isLoading && productCategories.length === 0 ? (
                                <EmptyState icon={<FolderTree />} title="Nu există categorii" description="Creează mai întâi o categorie activă pentru a putea adăuga produse." />
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
                                                            <Button
                                                                type="button"
                                                                variant="ghost"
                                                                size="icon"
                                                                onClick={() => {
                                                                    setEditingProduct(product)
                                                                    setProductDialogOpen(true)
                                                                }}
                                                            >
                                                                <Pencil />
                                                                <span className="sr-only">Editează produs</span>
                                                            </Button>
                                                            <Button
                                                                type="button"
                                                                variant="ghost"
                                                                size="icon"
                                                                disabled={deleteProduct.isPending}
                                                                onClick={() => {
                                                                    if (globalThis.confirm(`Ștergi produsul ${product.name}?`)) {
                                                                        deleteProduct.mutate(product.slug)
                                                                    }
                                                                }}
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
                            {productsQuery.isSuccess && productsQuery.data.data.length === 0 ? (
                                <EmptyState icon={<Package />} title="Nu există produse" description="Catalogul administrativ este gol în acest moment." />
                            ) : null}
                            {productsQuery.data && productsQuery.data.totalPages > 1 ? (
                                <div className="flex justify-center gap-2">
                                    <Button variant="outline" disabled={productsQuery.data.first} onClick={() => setProductPage((value) => value - 1)}>Anterior</Button>
                                    <Button variant="outline" disabled={productsQuery.data.last} onClick={() => setProductPage((value) => value + 1)}>Următor</Button>
                                </div>
                            ) : null}
                        </CardContent>
                    </Card>
                </TabsContent>

                <TabsContent value="categories" className="space-y-4">
                    <Card className="rounded-lg">
                        <CardHeader className="flex-row items-center justify-between gap-4">
                            <div>
                                <CardTitle>Categorii</CardTitle>
                                <p className="text-sm text-muted-foreground">CRUD complet pentru taxonomia consumată de catalog.</p>
                            </div>
                            <Button
                                type="button"
                                onClick={() => {
                                    setEditingCategory(null)
                                    setCategoryDialogOpen(true)
                                }}
                            >
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
                                                            <Button
                                                                type="button"
                                                                variant="ghost"
                                                                size="icon"
                                                                onClick={() => {
                                                                    setEditingCategory(category)
                                                                    setCategoryDialogOpen(true)
                                                                }}
                                                            >
                                                                <Pencil />
                                                                <span className="sr-only">Editează categoria</span>
                                                            </Button>
                                                            <Button
                                                                type="button"
                                                                variant="ghost"
                                                                size="icon"
                                                                disabled={deleteCategory.isPending}
                                                                onClick={() => {
                                                                    if (globalThis.confirm(`Ștergi categoria ${category.name}?`)) {
                                                                        deleteCategory.mutate(category.slug)
                                                                    }
                                                                }}
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
                            {categoriesQuery.isSuccess && categoriesQuery.data.data.length === 0 ? (
                                <EmptyState icon={<FolderTree />} title="Nu există categorii" description="Catalogul administrativ nu are încă taxonomii configurate." />
                            ) : null}
                        </CardContent>
                    </Card>
                </TabsContent>

                <TabsContent value="orders" className="space-y-4">
                    <Card className="rounded-lg">
                        <CardHeader className="flex-row items-center justify-between gap-4">
                            <div>
                                <CardTitle>Administrare comenzi</CardTitle>
                                <p className="text-sm text-muted-foreground">Listare completă și actualizare de status pentru toate comenzile.</p>
                            </div>
                            <div className="w-full max-w-xs">
                                <NativeSelect value={orderStatusFilter} onChange={(event) => {
                                    setOrderPage(0)
                                    setOrderStatusFilter(event.target.value)
                                }} className="w-full">
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
                                                                    <NativeSelect
                                                                        value={selectedStatus}
                                                                        onChange={(event) => setPendingStatuses((current) => ({
                                                                            ...current,
                                                                            [order.id]: event.target.value as OrderStatus,
                                                                        }))}
                                                                    >
                                                                        {orderStatuses.map((status) => (
                                                                            <NativeSelectOption key={status} value={status}>{orderStatusLabel(status)}</NativeSelectOption>
                                                                        ))}
                                                                    </NativeSelect>
                                                                    <Button
                                                                        type="button"
                                                                        variant="outline"
                                                                        disabled={selectedStatus === order.status || updateOrderStatus.isPending}
                                                                        onClick={() => updateOrderStatus.mutate({ orderId: order.id, status: selectedStatus })}
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
                            {ordersQuery.isSuccess && ordersQuery.data.data.length === 0 ? (
                                <EmptyState icon={<ShieldCheck />} title="Nu există comenzi pentru filtrul curent" description="Schimbă filtrul sau revino după plasarea altor comenzi." />
                            ) : null}
                            {ordersQuery.data && ordersQuery.data.totalPages > 1 ? (
                                <div className="flex justify-center gap-2">
                                    <Button variant="outline" disabled={ordersQuery.data.first} onClick={() => setOrderPage((value) => value - 1)}>Anterior</Button>
                                    <Button variant="outline" disabled={ordersQuery.data.last} onClick={() => setOrderPage((value) => value + 1)}>Următor</Button>
                                </div>
                            ) : null}
                        </CardContent>
                    </Card>
                </TabsContent>
            </Tabs>

            <Dialog
                open={productDialogOpen}
                onOpenChange={(open) => {
                    setProductDialogOpen(open)
                    if (!open) {
                        setEditingProduct(null)
                    }
                }}
            >
                <DialogContent className="max-w-3xl">
                    <DialogHeader>
                        <DialogTitle>{editingProduct ? 'Editează produsul' : 'Produs nou'}</DialogTitle>
                        <DialogDescription>
                            Configurează datele expuse în catalog pentru produsul selectat.
                        </DialogDescription>
                    </DialogHeader>
                    <form onSubmit={productForm.handleSubmit((values) => upsertProduct.mutate(values))} className="grid gap-4 md:grid-cols-2">
                        {productForm.formState.errors.root?.serverError?.message ? (
                            <div className="md:col-span-2 rounded-lg border border-destructive/30 bg-destructive/5 px-3 py-2 text-sm text-destructive">
                                {productForm.formState.errors.root.serverError.message}
                            </div>
                        ) : null}
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
                        {(['name', 'brand', 'model', 'country', 'currency'] as const).map((name) => (
                            <Field key={name}>
                                <FieldLabel>{name}</FieldLabel>
                                <Input {...productForm.register(name)} />
                                <FieldError>{productForm.formState.errors[name]?.message}</FieldError>
                            </Field>
                        ))}
                        {(['price', 'promotionalPrice', 'stock'] as const).map((name) => (
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
                            <Button type="button" variant="outline" onClick={() => setProductDialogOpen(false)}>Renunță</Button>
                            <Button type="submit" disabled={upsertProduct.isPending}>{editingProduct ? 'Salvează' : 'Creează'}</Button>
                        </div>
                    </form>
                </DialogContent>
            </Dialog>

            <Dialog
                open={categoryDialogOpen}
                onOpenChange={(open) => {
                    setCategoryDialogOpen(open)
                    if (!open) {
                        setEditingCategory(null)
                    }
                }}
            >
                <DialogContent className="max-w-2xl">
                    <DialogHeader>
                        <DialogTitle>{editingCategory ? 'Editează categoria' : 'Categorie nouă'}</DialogTitle>
                        <DialogDescription>
                            Actualizează taxonomia utilizată de catalog și de formularele administrative.
                        </DialogDescription>
                    </DialogHeader>
                    <form onSubmit={categoryForm.handleSubmit((values) => upsertCategory.mutate(values))} className="grid gap-4 md:grid-cols-2">
                        {(['slug', 'name', 'imageUrl', 'displayOrder'] as const).map((name) => (
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
                            <Button type="button" variant="outline" onClick={() => setCategoryDialogOpen(false)}>Renunță</Button>
                            <Button type="submit" disabled={upsertCategory.isPending}>{editingCategory ? 'Salvează' : 'Creează'}</Button>
                        </div>
                    </form>
                </DialogContent>
            </Dialog>
        </div>
    )
}