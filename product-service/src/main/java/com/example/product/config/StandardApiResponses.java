package com.example.product.config;

import com.example.product.exception.ApiErrorResponse;

import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ApiResponses({
        @ApiResponse(
                responseCode = "400",
                description = "Invalid request or validation error",
                content = @Content(
                        mediaType = "application/json",
                        schema = @Schema(
                                implementation = ApiErrorResponse.class
                        )
                )
        ),
        @ApiResponse(
                responseCode = "500",
                description = "Internal server error",
                content = @Content(
                        mediaType = "application/json",
                        schema = @Schema(
                                implementation = ApiErrorResponse.class
                        )
                )
        )
})
public @interface StandardApiResponses {
}