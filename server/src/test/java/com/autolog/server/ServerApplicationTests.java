package com.autolog.server;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.autolog.server.support.IntegrationTest;
import org.junit.jupiter.api.Test;

class ServerApplicationTests extends IntegrationTest {

	@Test
	void contextLoadsWithFlywaySchemaValidatedByJpa() {
	}

	@Test
	void healthIsPublic() throws Exception {
		mockMvc.perform(get("/actuator/health"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("UP"));
	}
}
