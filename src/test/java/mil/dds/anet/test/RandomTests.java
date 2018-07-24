package mil.dds.anet.test;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

import mil.dds.anet.beans.Person;
import mil.dds.anet.utils.DaoUtils;

//TODO: Probably rename this. 
public class RandomTests {

	@Test
	public void randomTests() { 
		Person p = new Person();
		assertThat(DaoUtils.getUuid(p)).isNull();
		p.setUuid("4");
		assertThat(DaoUtils.getUuid(p)).isEqualTo("4");
		
	}
	
}
