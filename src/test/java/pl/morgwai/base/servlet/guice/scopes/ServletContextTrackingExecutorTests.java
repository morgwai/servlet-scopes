// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.scopes;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Consumer;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.websocket.CloseReason;
import javax.websocket.CloseReason.CloseCodes;
import javax.websocket.Session;

import org.easymock.*;
import org.junit.*;
import pl.morgwai.base.guice.scopes.ContextBoundRunnable;
import pl.morgwai.base.servlet.guice.utils.StandaloneWebsocketContainerServletContext;
import pl.morgwai.base.utils.concurrent.NamingThreadFactory;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import static org.easymock.EasyMock.*;
import static org.junit.Assert.*;
import static org.junit.Assert.assertTrue;



public class ServletContextTrackingExecutorTests extends EasyMockSupport {



	final ServletContext mockDeployment = new StandaloneWebsocketContainerServletContext("/test");
	final Map<String, Object> attributes = new HashMap<>(3);

	@Mock HttpServletResponse servletResponse;
	boolean responseCommitted = false;
	final Capture<Integer> statusCapture = Capture.newInstance();

	@Mock HttpServletRequest servletRequest;
	final ServletModule servletModule = new ServletModule(mockDeployment);
	final ServletRequestContext requestCtx =
			new ServletRequestContext(servletRequest, servletModule.containerCallContextTracker);

	@Mock Session wsConnection;
	boolean wsConnectionClosed = false;
	final Capture<CloseReason> closeReasonCapture = Capture.newInstance();
	WebsocketConnectionProxy wsConnectionProxy;
	WebsocketConnectionContext wsConnectionCtx;
	WebsocketEventContext wsEventCtx;

	Runnable rejectedTask;
	Executor rejectingExecutor;
	final RejectedExecutionHandler rejectionHandler = (task, executor) -> {
		rejectedTask = task;
		rejectingExecutor = executor;
		throw new RejectedExecutionException("rejected " + task);
	};

	final CountDownLatch taskBlockingLatch = new CountDownLatch(1);
	final CountDownLatch blockingTasksStarted = new CountDownLatch(1);
	final Runnable blockingTask = () -> {
		blockingTasksStarted.countDown();
		try {
			taskBlockingLatch.await();
		} catch (InterruptedException ignored) {}
	};

	final ServletContextTrackingExecutor testSubject = servletModule.newContextTrackingExecutor(
		"testExecutor",
		1, 1,
		0L, MILLISECONDS,
		new LinkedBlockingQueue<>(1),
		new NamingThreadFactory("testExecutor"),
		rejectionHandler
	);



	@Before
	public void setupMocks() throws IOException {
		injectMocks(this);

		expect(servletRequest.getAttribute(anyString()))
			.andAnswer(() -> attributes.get((String) getCurrentArgument(0)))
			.anyTimes();
		servletRequest.setAttribute(anyString(), anyObject());
		expectLastCall()
			.andAnswer(() -> attributes.put(getCurrentArgument(0), getCurrentArgument(1)))
			.anyTimes();

		expect(servletResponse.isCommitted())
			.andAnswer(() -> responseCommitted)
			.anyTimes();
		servletResponse.sendError(captureInt(statusCapture));
		expectLastCall().andAnswer(() -> {
			responseCommitted = true;
			return null;  // Void
		}).times(0, 1);

		expect(wsConnection.getUserProperties())
			.andReturn(attributes)
			.anyTimes();
		expect(wsConnection.isOpen())
			.andAnswer(() -> !wsConnectionClosed)
			.anyTimes();
		wsConnection.close(capture(closeReasonCapture));
		expectLastCall().andAnswer(() -> {
			wsConnectionClosed = true;
			return null;  // Void
		}).times(0, 1);

		replayAll();

		wsConnectionProxy = new WebsocketConnectionProxy(
				wsConnection, servletModule.containerCallContextTracker, true);
		wsConnectionCtx = new WebsocketConnectionContext(wsConnectionProxy);
		wsEventCtx = new WebsocketEventContext(
				wsConnectionCtx, null, servletModule.containerCallContextTracker);
	}



	@After
	public void verifyMocks() {
		verifyAll();
	}



	public void testContextTracking(ContainerCallContext ctx) throws InterruptedException {
		final AssertionError[] asyncError = {null};
		final var taskFinished = new CountDownLatch(1);

		ctx.executeWithinSelf(() -> testSubject.execute(() -> {
			try {
				assertSame("context should be transferred when passing task to executor",
						ctx, servletModule.containerCallContextTracker.getCurrentContext());
			} catch (AssertionError e) {
				asyncError[0] = e;
			} finally {
				taskFinished.countDown();
			}
		}));
		assertTrue("task should complete",
				taskFinished.await(20L, MILLISECONDS));
		assertFalse("servletResponse should not be committed",
				responseCommitted);
		assertFalse("wsConnection should not be closed",
				wsConnectionClosed);
		if (asyncError[0] != null) throw asyncError[0];
	}

	@Test
	public void testServletRequestContextTracking() throws InterruptedException {
		testContextTracking(requestCtx);
	}

	@Test
	public void testWebsocketEventContextTracking() throws InterruptedException {
		testContextTracking(wsEventCtx);
	}



	public void testExecutionRejection(ContainerCallContext ctx, Consumer<Runnable> callUnderTest)
			throws Exception {
		final Runnable overloadingTask = () -> {};
		try {
			ctx.executeWithinSelf(() -> {
				testSubject.execute(blockingTask);
				assertTrue("blockingTask should start",
						blockingTasksStarted.await(50L, MILLISECONDS));
				testSubject.execute(() -> {});  // fill the queue

				callUnderTest.accept(overloadingTask);
				return null;  // Callable<Void>
			});
		} finally {
			taskBlockingLatch.countDown();
		}
		assertSame("rejectingExecutor should be testSubject",
				testSubject, rejectingExecutor);
		assertTrue("rejectedTask should be a ContextBoundRunnable",
				rejectedTask instanceof ContextBoundRunnable);
		assertSame("rejectedTask should be overloadingTask",
				overloadingTask, ((ContextBoundRunnable) rejectedTask).getBoundClosure());
	}

	@Test
	public void testWebsocketExecutionRejection() throws Exception {
		testExecutionRejection(wsEventCtx, (task) -> testSubject.execute(wsConnection, task));
		assertTrue("wsConnection should be closed",
				wsConnectionClosed);
		assertEquals("code reported to client should be TRY_AGAIN_LATER",
				CloseCodes.TRY_AGAIN_LATER, closeReasonCapture.getValue().getCloseCode());
	}

	@Test
	public void testWebsocketExecutionRejectionConnectionAlreadyClosed() throws Exception {
		wsConnectionClosed = true;
		testExecutionRejection(wsEventCtx, (task) -> testSubject.execute(wsConnection, task));
		assertFalse("close(...) should not be called if wsConnection was already closed",
				closeReasonCapture.hasCaptured());
	}

	@Test
	public void testServletExecutionRejection() throws Exception {
		testExecutionRejection(requestCtx, (task) -> testSubject.execute(servletResponse, task));
		assertTrue("servletResponse should be committed",
				responseCommitted);
		assertEquals("status reported to client should be SC_SERVICE_UNAVAILABLE",
				HttpServletResponse.SC_SERVICE_UNAVAILABLE, statusCapture.getValue().intValue());
	}

	@Test
	public void testServletExecutionRejectionResponseAlreadyCommitted() throws Exception {
		responseCommitted = true;
		testExecutionRejection(requestCtx, (task) -> testSubject.execute(servletResponse, task));
		assertFalse("status should not be sent if servletResponse was already committed",
				statusCapture.hasCaptured());
	}



	public void testGetRunningTasksPreservesContext(ContainerCallContext ctx)
			throws InterruptedException {
		ctx.executeWithinSelf(() -> testSubject.execute(blockingTask));
		assertTrue("blockingTask should start",
				blockingTasksStarted.await(50L, MILLISECONDS));
		final var runningTask = testSubject.getRunningTasks().get(0);
		assertTrue("runningTask should be a ContextBoundRunnable",
				runningTask instanceof ContextBoundRunnable);
		final var contextBoundRunningTask = (ContextBoundRunnable) runningTask;
		assertSame("runningTask should be blockingTask",
				blockingTask, contextBoundRunningTask.getBoundClosure());
		assertSame("ctx should be preserved during tryForceTerminate()",
				ctx, contextBoundRunningTask.getContexts().get(0));
	}

	@Test
	public void testGetRunningTasksPreservesServletContext() throws InterruptedException {
		testGetRunningTasksPreservesContext(requestCtx);
	}

	@Test
	public void testGetRunningTasksPreservesWebsocketContext() throws InterruptedException {
		testGetRunningTasksPreservesContext(wsEventCtx);
	}



	@After
	public void tryTerminate() {
		taskBlockingLatch.countDown();
		try {
			servletModule.enforceTerminationOfAllExecutors(50L, MILLISECONDS);
		} catch (InterruptedException ignored) {
		} finally {
			if ( !testSubject.isTerminated()) testSubject.shutdownNow();
		}
	}
}
