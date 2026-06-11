import { useEffect, useMemo, useState } from 'react'
import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'
import { FolderTree, Package, ShoppingBag } from 'lucide-react'
import { toast } from 'sonner'
import { SectionHeader } from '@/components/app/PageState'
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { categoryService, type CategoryDto } from '@/contracts/category'
import type { PagedResponseDto } from '@/contracts/common'
import { orderService, type OrderDto, type OrderStatus } from '@/contracts/order'
import { productService, type ProductDto } from '@/contracts/product'
import { problemDetail } from '@/lib/problem'
import {
    AdminCategoriesSection,
    AdminOrdersSection,
    AdminProductsSection,
    CategoryDialog,
    ProductDialog,
} from './AdminConsoleSections'
import {
    buildCategoryUpsertRequest,
    categoryFormSchema,
    categoryToFormValues,
    emptyCategoryFormValues,
    type CategoryFormInput,
    type CategoryFormValues,
} from './categoryForm'
import {
    buildProductUpsertRequest,
    emptyProductFormValues,
    getSelectableProductCategories,
    productFormSchema,
    productToFormValues,
    type ProductFormInput,
    type ProductFormValues,
} from './productForm'

const orderStatuses: OrderStatus[] = ['CREATED', 'CONFIRMED', 'PROCESSING', 'SHIPPED', 'DELIVERED', 'CANCELLED']

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
        defaultValues: emptyCategoryFormValues(),
    })

    const categoriesBySlug = useMemo(
        () => new Map(categories.map((category) => [category.slug, category.name] as const)),
        [categories],
    )

    const openCreateProductDialog = () => {
        setEditingProduct(null)
        setProductDialogOpen(true)
    }

    const openEditProductDialog = (product: ProductDto) => {
        setEditingProduct(product)
        setProductDialogOpen(true)
    }

    const openCreateCategoryDialog = () => {
        setEditingCategory(null)
        setCategoryDialogOpen(true)
    }

    const openEditCategoryDialog = (category: CategoryDto) => {
        setEditingCategory(category)
        setCategoryDialogOpen(true)
    }

    const handleProductDialogOpenChange = (open: boolean) => {
        setProductDialogOpen(open)
        if (!open) {
            setEditingProduct(null)
        }
    }

    const handleCategoryDialogOpenChange = (open: boolean) => {
        setCategoryDialogOpen(open)
        if (!open) {
            setEditingCategory(null)
        }
    }

    const handleDeleteProduct = (product: ProductDto) => {
        if (globalThis.confirm(`Ștergi produsul ${product.name}?`)) {
            deleteProduct.mutate(product.slug)
        }
    }

    const handleDeleteCategory = (category: CategoryDto) => {
        if (globalThis.confirm(`Ștergi categoria ${category.name}?`)) {
            deleteCategory.mutate(category.slug)
        }
    }

    const handleOrderStatusFilterChange = (status: string) => {
        setOrderPage(0)
        setOrderStatusFilter(status)
    }

    const handlePendingStatusChange = (orderId: string, status: OrderStatus) => {
        setPendingStatuses((current) => ({
            ...current,
            [orderId]: status,
        }))
    }

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

        categoryForm.reset(emptyCategoryFormValues())
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
            const payload = buildCategoryUpsertRequest(values, categories, editingCategory?.id)

            return editingCategory
                ? categoryService.update(editingCategory.slug, payload)
                : categoryService.create(payload)
        },
        onSuccess: () => {
            setCategoryDialogOpen(false)
            setEditingCategory(null)
            categoryForm.reset(emptyCategoryFormValues())
            queryClient.invalidateQueries({ queryKey: ['admin-categories'] })
            toast.success(editingCategory ? 'Categorie actualizată' : 'Categorie creată')
        },
        onError: (error) => {
            const detail = problemDetail(error, 'Operațiunea pe categorie a eșuat.')
            categoryForm.setError('root.serverError', { message: detail })
            toast.error(detail)
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
                description="Gestionați produsele, categoriile și comenzile platformei."
            />

            <Tabs defaultValue="products" className="gap-4">
                <TabsList>
                    <TabsTrigger value="products"><Package />Produse</TabsTrigger>
                    <TabsTrigger value="categories"><FolderTree />Categorii</TabsTrigger>
                    <TabsTrigger value="orders"><ShoppingBag />Comenzi</TabsTrigger>
                </TabsList>
                <AdminProductsSection
                    categoriesQuery={categoriesQuery}
                    productsQuery={productsQuery}
                    productCategories={productCategories}
                    categoriesBySlug={categoriesBySlug}
                    isDeletingProduct={deleteProduct.isPending}
                    onCreateProduct={openCreateProductDialog}
                    onEditProduct={openEditProductDialog}
                    onDeleteProduct={handleDeleteProduct}
                    onPreviousPage={() => setProductPage((value) => value - 1)}
                    onNextPage={() => setProductPage((value) => value + 1)}
                />

                <AdminCategoriesSection
                    categoriesQuery={categoriesQuery}
                    categories={categories}
                    isDeletingCategory={deleteCategory.isPending}
                    onCreateCategory={openCreateCategoryDialog}
                    onEditCategory={openEditCategoryDialog}
                    onDeleteCategory={handleDeleteCategory}
                />

                <AdminOrdersSection
                    ordersQuery={ordersQuery}
                    orderStatuses={orderStatuses}
                    orderStatusFilter={orderStatusFilter}
                    pendingStatuses={pendingStatuses}
                    isUpdatingOrderStatus={updateOrderStatus.isPending}
                    onOrderStatusFilterChange={handleOrderStatusFilterChange}
                    onPendingStatusChange={handlePendingStatusChange}
                    onUpdateOrderStatus={(orderId, status) => updateOrderStatus.mutate({ orderId, status })}
                    onPreviousPage={() => setOrderPage((value) => value - 1)}
                    onNextPage={() => setOrderPage((value) => value + 1)}
                />
            </Tabs>

            <ProductDialog
                open={productDialogOpen}
                editingProduct={editingProduct}
                productCategories={productCategories}
                productForm={productForm}
                isPending={upsertProduct.isPending}
                onOpenChange={handleProductDialogOpenChange}
                onSubmit={(values) => upsertProduct.mutate(values)}
            />

            <CategoryDialog
                open={categoryDialogOpen}
                editingCategory={editingCategory}
                categories={categories}
                categoryForm={categoryForm}
                isPending={upsertCategory.isPending}
                onOpenChange={handleCategoryDialogOpenChange}
                onSubmit={(values) => upsertCategory.mutate(values)}
            />
        </div>
    )
}