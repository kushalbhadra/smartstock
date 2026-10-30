package com.smartstock.smartstock;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.ActiveProfiles;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class SmartstockApplicationTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void contextLoads() {
	}

	@Test
	void apiRequiresAuthentication() throws Exception {
		mockMvc.perform(get("/api/products"))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/notifications/unread-count"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	@WithMockUser(username = "test-admin@example.invalid", roles = "ADMIN")
	void authenticatedUserCanReadAndAcknowledgeOwnNotifications()
			throws Exception {
		mockMvc.perform(get("/api/notifications/unread-count"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.count").value(0));

		mockMvc.perform(get("/api/notifications/recent"))
				.andExpect(status().isOk());

		mockMvc.perform(get("/api/notifications"))
				.andExpect(status().isOk());

		mockMvc.perform(get("/api/notifications")
						.param("type", "LOW_STOCK")
						.param("active", "true")
						.param("unread", "true")
						.param("page", "0")
						.param("size", "10"))
				.andExpect(status().isOk());

		mockMvc.perform(patch("/api/notifications/read-all"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.updated").value(0));

		mockMvc.perform(patch("/api/notifications/999999/read"))
				.andExpect(status().isNotFound());
	}

	@Test
	@WithMockUser(username = "viewer@smartstock.com", roles = "VIEWER")
	void viewerCanReadProductsButCannotExportOrMutateThem()
			throws Exception {
		mockMvc.perform(get("/api/products"))
				.andExpect(status().isOk());

		mockMvc.perform(get("/api/products/export"))
				.andExpect(status().isForbidden());

		mockMvc.perform(post("/api/products")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":"Pen","sku":"PEN-1",
								"category":"Stationery","unit":"piece",
								"price":1}
								"""))
				.andExpect(status().isForbidden());
	}

	@Test
	@WithMockUser(username = "viewer@smartstock.com", roles = "VIEWER")
	void viewerCannotReadAdminSettings() throws Exception {
		mockMvc.perform(get("/api/settings"))
				.andExpect(status().isForbidden());
	}
}
