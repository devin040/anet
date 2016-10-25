package mil.dds.anet.test.resources;

import static org.assertj.core.api.Assertions.assertThat;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Response;

import org.junit.ClassRule;
import org.junit.Test;

import io.dropwizard.client.JerseyClientBuilder;
import io.dropwizard.testing.junit.DropwizardAppRule;
import mil.dds.anet.AnetApplication;
import mil.dds.anet.beans.Group;
import mil.dds.anet.beans.Person;
import mil.dds.anet.config.AnetConfiguration;
import mil.dds.anet.test.beans.PersonTest;

public class GroupsResourceTest {

	@ClassRule
    public static final DropwizardAppRule<AnetConfiguration> RULE =
            new DropwizardAppRule<AnetConfiguration>(AnetApplication.class, "anet.yml");
	
	public static Client client;
	
	public GroupsResourceTest() { 
		if (client == null) { 
			client = new JerseyClientBuilder(RULE.getEnvironment()).build("groups test client");
		}
	}
	
	@Test
	public void createGroup() { 
		Group g = new Group();
		g.setName("A Test Group");
		
		Group created = client.target(String.format("http://localhost:%d/groups/new", RULE.getLocalPort()))
			.request()
			.post(Entity.json(g), Group.class);
		assertThat(created.getName()).isEqualTo(g.getName());
		
		Group returned = client.target(String.format("http://localhost:%d/groups/%d", RULE.getLocalPort(), created.getId()))
			.request()
			.get(Group.class);
		assertThat(created).isEqualTo(returned);
		
		//Create a couple people and add them to the group
		Person jack = client.target(String.format("http://localhost:%d/people/new", RULE.getLocalPort()))
				.request()
				.post(Entity.json(PersonTest.getJackJackson()), Person.class);
		Person steve = client.target(String.format("http://localhost:%d/people/new", RULE.getLocalPort()))
				.request()
				.post(Entity.json(PersonTest.getSteveSteveson()), Person.class);
		
		Response resp = client.target(String.format("http://localhost:%d/groups/addMember?groupId=%d&personId=%d",
				RULE.getLocalPort(), returned.getId(), jack.getId()))
				.request()
				.get();
		assertThat(resp.getStatus()).isEqualTo(200);
		resp = client.target(String.format("http://localhost:%d/groups/addMember?groupId=%d&personId=%d", 
				RULE.getLocalPort(), returned.getId(), steve.getId()))
				.request()
				.get();
		assertThat(resp.getStatus()).isEqualTo(200);
				
		//Verify that users are in the group
		returned = client.target(String.format("http://localhost:%d/groups/%d", RULE.getLocalPort(), created.getId()))
				.request()
				.get(Group.class);
		assertThat(returned.getMembers().size()).isEqualTo(2);
		assertThat(returned.getMembers()).contains(steve, jack);
		
		//Remove a user from the group, verify they are no longer there
		resp = client.target(String.format("http://localhost:%d/groups/removeMember?groupId=%d&personId=%d", 
				RULE.getLocalPort(), returned.getId(), steve.getId()))
				.request()
				.get();
		assertThat(resp.getStatus()).isEqualTo(200);
		returned = client.target(String.format("http://localhost:%d/groups/%d", RULE.getLocalPort(), created.getId()))
				.request()
				.get(Group.class);
		assertThat(returned.getMembers().size()).isEqualTo(1);
		assertThat(returned.getMembers()).doesNotContain(steve);
	}
	
	@Test
	public void changeGroup() { 
		Group g = new Group();
		g.setName("A Test-Change Group");
		
		Group created = client.target(String.format("http://localhost:%d/groups/new", RULE.getLocalPort()))
				.request()
				.post(Entity.json(g), Group.class);
		assertThat(created.getName()).isEqualTo(g.getName());
		
		created.setName("A Changed Name");
		Response resp = client.target(String.format("http://localhost:%d/groups/rename", RULE.getLocalPort()))
				.request()
				.post(Entity.json(created));
		assertThat(resp.getStatus()).isEqualTo(200);
		
		Group returned = client.target(String.format("http://localhost:%d/groups/%d", RULE.getLocalPort(), created.getId()))
				.request()
				.get(Group.class);
		assertThat(created).isEqualTo(returned);
	}
	
	@Test
	public void deleteGroup() { 
		Group g = new Group();
		g.setName("A Group to Delete");
		
		Group created = client.target(String.format("http://localhost:%d/groups/new", RULE.getLocalPort()))
				.request()
				.post(Entity.json(g), Group.class);
		assertThat(created.getName()).isEqualTo(g.getName());
		
		Response resp = client.target(String.format("http://localhost:%d/groups/%d", RULE.getLocalPort(), created.getId()))
				.request()
				.delete();
		assertThat(resp.getStatus()).isEqualTo(200);
		
		Group returned = client.target(String.format("http://localhost:%d/groups/%d", RULE.getLocalPort(), created.getId()))
				.request()
				.get(Group.class);
		assertThat(returned).isNull();
	}
}