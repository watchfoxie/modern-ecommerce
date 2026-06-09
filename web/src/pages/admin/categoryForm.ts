import { z } from 'zod'
import type { CategoryDto, UpsertCategoryRequest } from '@/contracts/category'

const CATEGORY_SLUG_PATTERN = /^[a-z0-9]+(?:-[a-z0-9]+)*$/
const ASSET_OR_URL_PATTERN = /^(?:https?:\/\/|\/|static\/).+/i

export const categoryFormSchema = z
    .object({
        slug: z
            .string()
            .trim()
            .min(2, 'Slug-ul trebuie să aibă cel puțin 2 caractere.')
            .max(140, 'Slug-ul nu poate depăși 140 de caractere.')
            .regex(CATEGORY_SLUG_PATTERN, 'Slug-ul trebuie să fie lowercase și compatibil URL.'),
        name: z
            .string()
            .trim()
            .min(2, 'Numele trebuie să aibă cel puțin 2 caractere.')
            .max(120, 'Numele nu poate depăși 120 de caractere.'),
        description: z
            .string()
            .trim()
            .min(1, 'Descrierea este obligatorie.')
            .max(500, 'Descrierea nu poate depăși 500 de caractere.'),
        parentId: z.string().optional(),
        imageUrl: z.string().optional(),
        displayOrder: z.coerce.number().int().min(0, 'Ordinea nu poate fi negativă.'),
        isActive: z.boolean(),
    })
    .superRefine((values, context) => {
        const imageUrl = values.imageUrl?.trim()
        if (imageUrl && !ASSET_OR_URL_PATTERN.test(imageUrl)) {
            context.addIssue({
                code: 'custom',
                path: ['imageUrl'],
                message: 'Imaginea trebuie să fie un URL valid sau o cale statică din proiect.',
            })
        }
    })

export type CategoryFormInput = z.input<typeof categoryFormSchema>
export type CategoryFormValues = z.output<typeof categoryFormSchema>

export function emptyCategoryFormValues(): CategoryFormValues {
    return {
        slug: '',
        name: '',
        description: '',
        parentId: '',
        imageUrl: '',
        displayOrder: 0,
        isActive: true,
    }
}

export function categoryToFormValues(category: CategoryDto): CategoryFormValues {
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

export function buildCategoryUpsertRequest(
    values: CategoryFormValues,
    categories: CategoryDto[],
    editingCategoryId?: string,
): UpsertCategoryRequest {
    const parentId = values.parentId?.trim() || null
    if (parentId) {
        if (parentId === editingCategoryId) {
            throw new Error('Categoria nu se poate selecta pe sine drept părinte.')
        }

        const parentExists = categories.some((category) => category.id === parentId)
        if (!parentExists) {
            throw new Error('Categoria părinte selectată nu mai este disponibilă.')
        }
    }

    return {
        slug: values.slug.trim(),
        name: values.name.trim(),
        description: values.description.trim(),
        parentId,
        imageUrl: values.imageUrl?.trim() || undefined,
        displayOrder: values.displayOrder,
        isActive: values.isActive,
    }
}