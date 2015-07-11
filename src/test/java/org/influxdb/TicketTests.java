package org.influxdb;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.influxdb.InfluxDB.LogLevel;
import org.influxdb.dto.BatchPoints;
import org.influxdb.dto.Point;
import org.influxdb.dto.Pong;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.DockerClientConfig;

/**
 * Test the InfluxDB API.
 * 
 * @author stefan.majer [at] gmail.com
 * 
 */
@Test
public class TicketTests {

	protected InfluxDB influxDB;
	protected DockerClient dockerClient;
	protected CreateContainerResponse container;

	/**
	 * Create a influxDB connection before all tests start.
	 * 
	 * @throws InterruptedException
	 * @throws IOException
	 */
	@BeforeClass
	public void setUp() throws InterruptedException, IOException {
		// Disable logging for the DockerClient.
		Logger.getLogger("com.sun.jersey").setLevel(Level.OFF);
		DockerClientConfig config = DockerClientConfig
				.createDefaultConfigBuilder()
				.withVersion("1.16")
				.withUri("tcp://localhost:4243")
				.withUsername("roott")
				.withPassword("root")
				.build();
		this.dockerClient = DockerClientBuilder.getInstance(config).build();
		String ip = "127.0.0.1";
		this.influxDB = InfluxDBFactory.connect("http://" + ip + ":8086", "root", "root");
		boolean influxDBstarted = false;
		do {
			Pong response;
			try {
				response = this.influxDB.ping();
				System.out.println(response);
				if (!response.getVersion().equalsIgnoreCase("unknown")) {
					influxDBstarted = true;
				}
			} catch (Exception e) {
				// NOOP intentional
				e.printStackTrace();
			}
			Thread.sleep(100L);
		} while (!influxDBstarted);
		this.influxDB.setLogLevel(LogLevel.FULL);
		// String logs = CharStreams.toString(new InputStreamReader(containerLogsStream,
		// Charsets.UTF_8));
		System.out.println("##################################################################################");
		// System.out.println("Container Logs: \n" + logs);
		System.out.println("#  Connected to InfluxDB Version: " + this.influxDB.version() + " #");
		System.out.println("##################################################################################");
	}

	/**
	 * Ensure all Databases created get dropped afterwards.
	 */
	@AfterClass
	public void tearDown() {
		System.out.println("Kill the Docker container");
		// this.dockerClient.killContainerCmd(this.container.getId()).exec();
	}

	/**
	 * Test for ticket #38
	 *
	 */
	@Test(enabled = true)
	public void testTicket38() {
		String dbName = "ticket38_" + System.currentTimeMillis();
		this.influxDB.createDatabase(dbName);
		Point point1 = Point
				.measurement("metric")
				.time(System.currentTimeMillis(), TimeUnit.MILLISECONDS)
				.field("value", 5)
				.tag("host", "host A")
				.tag("host", "host-B")
				.tag("host", "host-\"C")
				.tag("region", "region")
				.build();
		this.influxDB.write(dbName, "default", point1);
		this.influxDB.deleteDatabase(dbName);
	}

	/**
	 * Test for ticket #39
	 *
	 */
	@Test(enabled = true)
	public void testTicket39() {
		String dbName = "ticket39_" + System.currentTimeMillis();
		this.influxDB.createDatabase(dbName);
		BatchPoints batchPoints = BatchPoints
				.database(dbName)
				.tag("async", "true")
				.retentionPolicy("default")
				.consistency(InfluxDB.ConsistencyLevel.ALL)
				.build();
		Point.Builder builder = Point.measurement("my_type");
		builder.field("my_field", "string_value");
		Point point = builder.build();
		batchPoints.point(point);
		this.influxDB.write(batchPoints);
		this.influxDB.deleteDatabase(dbName);
	}

	/**
	 * Test for ticket #40
	 */
	@Test(enabled = true)
	public void testTicket40() {
		String dbName = "ticket40_" + System.currentTimeMillis();
		this.influxDB.createDatabase(dbName);
		this.influxDB.enableBatch(100, 100, TimeUnit.MICROSECONDS);
		for (int i = 0; i < 1000; i++) {
			Point point = Point.measurement("cpu").field("idle", 99).build();
			this.influxDB.write(dbName, "default", point);
		}
		this.influxDB.deleteDatabase(dbName);
	}

	/**
	 * Test for ticket #44
	 */
	@Test(enabled = true)
	public void testTicket44() {
		Point point = Point.measurement("test").time(1, TimeUnit.MICROSECONDS).field("a", 1).build();
		assertThat(point.lineProtocol()).asString().isEqualTo("test a=1 1000");

		point = Point.measurement("test").time(1, TimeUnit.MILLISECONDS).field("a", 1).build();
		assertThat(point.lineProtocol()).asString().isEqualTo("test a=1 1000000");

		point = Point.measurement("test").time(1, TimeUnit.NANOSECONDS).field("a", 1).build();
		BatchPoints batchPoints = BatchPoints.database("db").point(point).build();
		assertThat(batchPoints.lineProtocol()).asString().isEqualTo("test a=1 1\n");

		point = Point.measurement("test").time(1, TimeUnit.MICROSECONDS).field("a", 1).build();
		batchPoints = BatchPoints.database("db").point(point).build();
		assertThat(batchPoints.lineProtocol()).asString().isEqualTo("test a=1 1000\n");

		point = Point.measurement("test").time(1, TimeUnit.MILLISECONDS).field("a", 1).build();
		batchPoints = BatchPoints.database("db").point(point).build();
		assertThat(batchPoints.lineProtocol()).asString().isEqualTo("test a=1 1000000\n");

		point = Point.measurement("test").field("a", 1).time(1, TimeUnit.MILLISECONDS).build();
		batchPoints = BatchPoints.database("db").build();
		batchPoints = batchPoints.point(point);
		assertThat(batchPoints.lineProtocol()).asString().isEqualTo("test a=1 1000000\n");

	}

	@Test
	public void testTicket54() {
		Byte byteNumber = 100;
		Point point = Point.measurement("test").time(1, TimeUnit.NANOSECONDS).field("a", byteNumber).build();
		assertThat(point.lineProtocol()).asString().isEqualTo("test a=100 1");

		int intNumber = 100000000;
		point = Point.measurement("test").time(1, TimeUnit.NANOSECONDS).field("a", intNumber).build();
		assertThat(point.lineProtocol()).asString().isEqualTo("test a=100000000 1");

		Integer integerNumber = 100000000;
		point = Point.measurement("test").time(1, TimeUnit.NANOSECONDS).field("a", integerNumber).build();
		assertThat(point.lineProtocol()).asString().isEqualTo("test a=100000000 1");

		AtomicInteger atomicIntegerNumber = new AtomicInteger(100000000);
		point = Point.measurement("test").time(1, TimeUnit.NANOSECONDS).field("a", atomicIntegerNumber).build();
		assertThat(point.lineProtocol()).asString().isEqualTo("test a=100000000 1");

		Long longNumber = 1000000000000000000L;
		point = Point.measurement("test").time(1, TimeUnit.NANOSECONDS).field("a", longNumber).build();
		assertThat(point.lineProtocol()).asString().isEqualTo("test a=1000000000000000000 1");

		AtomicLong atomicLongNumber = new AtomicLong(1000000000000000000L);
		point = Point.measurement("test").time(1, TimeUnit.NANOSECONDS).field("a", atomicLongNumber).build();
		assertThat(point.lineProtocol()).asString().isEqualTo("test a=1000000000000000000 1");

		BigInteger bigIntegerNumber = BigInteger.valueOf(100000000);
		point = Point.measurement("test").time(1, TimeUnit.NANOSECONDS).field("a", bigIntegerNumber).build();
		assertThat(point.lineProtocol()).asString().isEqualTo("test a=100000000 1");

		Double doubleNumber = Double.valueOf(100000000.0001);
		point = Point.measurement("test").time(1, TimeUnit.NANOSECONDS).field("a", doubleNumber).build();
		assertThat(point.lineProtocol()).asString().isEqualTo("test a=100000000.0001 1");

		Float floatNumber = Float.valueOf(0.1f);
		point = Point.measurement("test").time(1, TimeUnit.NANOSECONDS).field("a", floatNumber).build();
		assertThat(point.lineProtocol()).asString().startsWith("test a=0.10");

		BigDecimal bigDecimalNumber = BigDecimal.valueOf(100000000.00000001);
		point = Point.measurement("test").time(1, TimeUnit.NANOSECONDS).field("a", bigDecimalNumber).build();
		assertThat(point.lineProtocol()).asString().isEqualTo("test a=100000000.00000001 1");
	}
}