package com.example.ecom.product;

import com.example.ecom.api.ApiExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class ProductControllerTest {
    @Mock ProductService service;
    MockMvc mvc;
    @BeforeEach void setup() { mvc = MockMvcBuilders.standaloneSetup(new ProductController(service))
            .setControllerAdvice(new ApiExceptionHandler()).build(); }
    @Test void combinesCategorySearchAndPage() throws Exception {
        when(service.findAll(any())).thenReturn(new ProductPageResponse(List.of(), 1, 2, 3));
        mvc.perform(get("/api/v1/products").queryParam("categoryId", "4").queryParam("q", " blue ")
                .queryParam("page", "1").queryParam("size", "2").queryParam("sort", "price,desc"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalCount").value(3));
        ArgumentCaptor<ProductQuery> captor = ArgumentCaptor.forClass(ProductQuery.class);
        verify(service).findAll(captor.capture());
        assertThat(captor.getValue().categoryId()).isEqualTo(4L);
        assertThat(captor.getValue().searchTerm()).isEqualTo("blue");
        assertThat(captor.getValue().pageable().getPageNumber()).isEqualTo(1);
    }
    @Test void invalidQueryKeepsProblemFormat() throws Exception {
        mvc.perform(get("/api/v1/products").queryParam("sort", "nonsense"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.title").value("Invalid product query"));
        mvc.perform(get("/api/v1/products").queryParam("categoryId", "oops"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.parameter").value("categoryId"));
        verifyNoInteractions(service);
    }
    @Test void missingProductKeepsProblemFormat() throws Exception {
        when(service.findById(99)).thenThrow(new ProductNotFoundException(99));
        mvc.perform(get("/api/v1/products/99")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.productId").value(99));
    }
}
