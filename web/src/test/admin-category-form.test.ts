import { describe, expect, it } from 'vitest';
import type { CategoryDto } from '@/contracts/category';
import {
    buildCategoryUpsertRequest,
    categoryFormSchema,
    categoryToFormValues,
} from '@/pages/admin/categoryForm';

const categories: CategoryDto[] = [
    { id: 'cat-root', slug: 'electronics', name: 'Electronice', description: 'Root', displayOrder: 1, isActive: true },
    { id: 'cat-smartphones', slug: 'smartphones', name: 'Smartphone-uri', description: 'Telefoane', parentId: 'cat-root', imageUrl: 'static/assets/images/prod-images/categories-offers/generic-smartphones-1.png', displayOrder: 2, isActive: true },
];

describe('admin category form helpers', () => {
    it('maps an existing category to editable form values', () => {
        const values = categoryToFormValues(categories[1]);

        expect(values.parentId).toBe('cat-root');
        expect(values.imageUrl).toContain('generic-smartphones-1.png');
    });

    it('normalizes blank optional values while preserving required fields', () => {
        const payload = buildCategoryUpsertRequest({
            slug: 'laptops',
            name: 'Laptop-uri',
            description: 'Toate laptopurile disponibile.',
            parentId: '',
            imageUrl: '',
            displayOrder: 3,
            isActive: true,
        }, categories);

        expect(payload.parentId).toBeNull();
        expect(payload.imageUrl).toBeUndefined();
        expect(payload.description).toBe('Toate laptopurile disponibile.');
    });

    it('rejects invalid category image paths in the schema', () => {
        const result = categoryFormSchema.safeParse({
            slug: 'laptops',
            name: 'Laptop-uri',
            description: 'Toate laptopurile disponibile.',
            parentId: '',
            imageUrl: 'ftp://invalid-image',
            displayOrder: 3,
            isActive: true,
        });

        expect(result.success).toBe(false);
        if (!result.success) {
            const hasImageIssue = result.error.issues.some((issue) => issue.path.includes('imageUrl'));
            expect(hasImageIssue).toBe(true);
        }
    });

    it('rejects selecting the edited category as its own parent', () => {
        expect(() => buildCategoryUpsertRequest({
            slug: 'smartphones',
            name: 'Smartphone-uri',
            description: 'Telefoane',
            parentId: 'cat-smartphones',
            imageUrl: 'static/assets/images/prod-images/categories-offers/generic-smartphones-1.png',
            displayOrder: 2,
            isActive: true,
        }, categories, 'cat-smartphones')).toThrow('Categoria nu se poate selecta pe sine drept părinte.');
    });
});