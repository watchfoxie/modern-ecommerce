import { describe, expect, it } from 'vitest';
import type { CategoryDto } from '@/contracts/category';
import type { ProductDto } from '@/contracts/product';
import {
    buildProductUpsertRequest,
    getSelectableProductCategories,
    productFormSchema,
    productToFormValues,
} from '@/pages/admin/productForm';

const categories: CategoryDto[] = [
    { id: 'cat-smartphones', slug: 'smartphones', name: 'Smartphone-uri', displayOrder: 1, isActive: true },
    { id: 'cat-laptops', slug: 'laptops', name: 'Laptop-uri', displayOrder: 2, isActive: true },
    { id: 'cat-offers', slug: 'offers', name: 'Oferte', displayOrder: 3, isActive: true },
];

describe('admin product form helpers', () => {
    it('filters out the virtual offers category from selectable product categories', () => {
        const selectableCategories = getSelectableProductCategories(categories).map((category) => category.slug);
        expect(selectableCategories).toEqual(['smartphones', 'laptops']);
    });

    it('infers the base product category from an offers product image path', () => {
        const product: ProductDto = {
            id: 'product-1',
            categoryId: 'cat-offers',
            categorySlug: 'offers',
            slug: 'huawei-matebook-d-16',
            name: 'Huawei MateBook D 16',
            brand: 'Huawei',
            model: 'MateBook D 16',
            country: 'China',
            price: 28357.73,
            promotionalPrice: 24303.73,
            currency: 'MDL',
            stock: 60,
            imageUrls: ['static/assets/images/prod-images/laptops/huawei/matebook-d-16-2024-1.png'],
            specs: { screenSize: '16 inch' },
            isActive: true,
        };

        expect(productToFormValues(product, categories).categoryId).toBe('cat-laptops');
    });

    it('normalizes a promotional price equal to the base price to null on submit', () => {
        const payload = buildProductUpsertRequest(
            {
                categoryId: 'cat-laptops',
                slug: 'huawei-matebook-d-16',
                name: 'Huawei MateBook D 16',
                brand: 'Huawei',
                model: 'MateBook D 16 2024',
                country: 'China',
                price: 28357.73,
                promotionalPrice: '28357.73',
                currency: 'MDL',
                stock: 60,
                imageUrlsText: 'static/assets/images/prod-images/laptops/huawei/matebook-d-16-2024-1.png',
                specsText: 'screenSize: 16 inch\nprocessor: Intel Core i9',
                isActive: true,
            },
            getSelectableProductCategories(categories),
        );

        expect(payload.categorySlug).toBe('laptops');
        expect(payload.promotionalPrice).toBeNull();
    });

    it('rejects unsupported specification keys in the form schema', () => {
        const result = productFormSchema.safeParse({
            categoryId: 'cat-laptops',
            slug: 'huawei-matebook-d-16',
            name: 'Huawei MateBook D 16',
            brand: 'Huawei',
            model: 'MateBook D 16 2024',
            country: 'China',
            price: 28357.73,
            promotionalPrice: '24000',
            currency: 'MDL',
            stock: 60,
            imageUrlsText: 'static/assets/images/prod-images/laptops/huawei/matebook-d-16-2024-1.png',
            specsText: 'weight: 2kg',
            isActive: true,
        });

        expect(result.success).toBe(false);
        if (!result.success) {
            const hasSpecsIssue = result.error.issues.some((issue) => issue.path.includes('specsText'));
            expect(hasSpecsIssue).toBe(true);
        }
    });
});