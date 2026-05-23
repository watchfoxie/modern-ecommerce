package md.services.category_service.api;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Standard offset-paginated response envelope.")
public record PagedResponseDto<T>(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<T> data,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) int page,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) int size,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) long totalElements,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) int totalPages,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean first,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean last) {
}
