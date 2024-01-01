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

	@Mock final HttpServletResponse servletResponse = mock(HttpServletResponse.class);
	boolean responseCommitted = false;
	final Capture<Integer> statusCapture = Capture.newInstance();
	@Mock final HttpServletRequest servletRequest = mock(HttpServletRequest.class);
	final ServletModule servletModule = new ServletModule(mockDeployment);
	final ServletRequestContext requestCtx =
			new ServletRequestContext(servletRequest, servletModule.containerCallContextTracker);

	@Mock final Session wsConnection = mock(Session.class);
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

	ServletContextTrackingExecutor testSubject;
	CountDownLatch taskBlockingLatch;



	@Before
	public void setupMocks() throws IOException {
		expect(servletRequest.getAttribute(anyString()))
			.andAnswer(() -> attributes.get((String) getCurrentArgument(0))).anyTimes();

		servletRequest.setAttribute(anyString(), anyObject());
		expectLastCall().andAnswer(
			() -> attributes.put(getCurrentArgument(0), getCurrentArgument(1))
		).anyTimes();

		expect(servletResponse.isCommitted()).andAnswer(() -> responseCommitted).anyTimes();
		servletResponse.sendError(captureInt(statusCapture));
		expectLastCall().andAnswer(() -> {
			responseCommitted = true;
			return null;
		}).times(0, 1);

		expect(wsConnection.getUserProperties()).andReturn(attributes).anyTimes();
		expect(wsConnection.isOpen()).andAnswer(() -> !wsConnectionClosed).anyTimes();
		wsConnection.close(capture(closeReasonCapture));
		expectLastCall().andAnswer(() -> {
			wsConnectionClosed = true;
			return null;
		}).times(0, 1);

		replayAll();

		wsConnectionProxy = new WebsocketConnectionProxy(
				wsConnection, servletModule.containerCallContextTracker, true);
		wsConnectionCtx = new WebsocketConnectionContext(wsConnectionProxy);
		wsEventCtx = new WebsocketEventContext(
				wsConnectionCtx, null, servletModule.containerCallContextTracker);

		taskBlockingLatch = new CountDownLatch(1);
		testSubject = servletModule.newContextTrackingExecutor(
			"testExecutor",
			1, 1,
			0L, MILLISECONDS,
			new LinkedBlockingQueue<>(1),
			new NamingThreadFactory("testExecutor"),
			rejectionHandler
		);
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
		assertTrue("task should complete", taskFinished.await(20L, MILLISECONDS));
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



	public void testExecutionRejection(ContainerCallContext ctx, Consumer<Runnable> callUnderTest) {
		final Runnable overloadingTask = () -> {};
		try {
			ctx.executeWithinSelf(() -> {
				testSubject.execute(() -> {  // make worker busy
					try {
						taskBlockingLatch.await();
					} catch (InterruptedException ignored) {}
				});
				testSubject.execute(() -> {});  // fill the queue

				callUnderTest.accept(overloadingTask);
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
	public void testWebsocketExecutionRejection() {
		testExecutionRejection(wsEventCtx, (task) -> testSubject.execute(wsConnection, task));
		assertEquals("code reported to client should be TRY_AGAIN_LATER",
				CloseCodes.TRY_AGAIN_LATER, closeReasonCapture.getValue().getCloseCode());
	}

	@Test
	public void testWebsocketExecutionRejectionConnectionAlreadyClosed() {
		wsConnectionClosed = true;
		testExecutionRejection(wsEventCtx, (task) -> testSubject.execute(wsConnection, task));
		assertFalse("close(...) should not be called if wsConnection was already closed",
				closeReasonCapture.hasCaptured());
	}

	@Test
	public void testServletExecutionRejection() {
		testExecutionRejection(requestCtx, (task) -> testSubject.execute(servletResponse, task));
		assertEquals("status reported to client should be SC_SERVICE_UNAVAILABLE",
				HttpServletResponse.SC_SERVICE_UNAVAILABLE, statusCapture.getValue().intValue());
	}

	@Test
	public void testServletExecutionRejectionResponseAlreadyCommitted() {
		responseCommitted = true;
		testExecutionRejection(requestCtx, (task) -> testSubject.execute(servletResponse, task));
		assertFalse("status should not be sent if servletResponse was already committed",
				statusCapture.hasCaptured());
	}



	public void testTryForceTerminatePreservesContext(ContainerCallContext ctx)
			throws InterruptedException {
		final var blockingTasksStarted = new CountDownLatch(1);
		final var blockingTaskFinished = new CountDownLatch(1);
		final Runnable blockingTask = () -> {
			blockingTasksStarted.countDown();
			try {
				taskBlockingLatch.await();
			} catch (InterruptedException ignored) {}
			blockingTaskFinished.countDown();
		};
		final Runnable queuedTask = () -> {};
		ctx.executeWithinSelf(() -> {
			testSubject.execute(blockingTask);
			testSubject.execute(queuedTask);
		});
		assertTrue("blocking task should start",
				blockingTasksStarted.await(50L, MILLISECONDS));

		testSubject.shutdown();
		final var aftermath = testSubject.tryForceTerminate();
		assertTrue("blockingTask should finish after tryForceTerminate()",
				blockingTaskFinished.await(50L, MILLISECONDS));
		assertEquals("1 task should be running in the aftermath",
				1, aftermath.runningTasks.size());
		assertEquals("1 task should be unexecuted in the aftermath",
				1, aftermath.unexecutedTasks.size());
		final var runningTask = aftermath.runningTasks.get(0);
		final var unexecutedTask = aftermath.unexecutedTasks.get(0);
		assertTrue("runningTask should be a ContextBoundRunnable",
				runningTask instanceof ContextBoundRunnable);
		final var contextBoundRunningTask = (ContextBoundRunnable) runningTask;
		assertTrue("unexecutedTask should be a ContextBoundRunnable",
				unexecutedTask instanceof ContextBoundRunnable);
		final var contextBoundUnexecutedTask = (ContextBoundRunnable) unexecutedTask;
		assertSame("runningTask should be blockingTask",
				blockingTask, contextBoundRunningTask.getBoundClosure());
		assertSame("unexecutedTask should be queuedTask",
				queuedTask, contextBoundUnexecutedTask.getBoundClosure());
		assertSame("ctx should be preserved during tryForceTerminate()",
				ctx, contextBoundRunningTask.getContexts().get(0));
		assertSame("ctx should be preserved during tryForceTerminate()",
				ctx, contextBoundUnexecutedTask.getContexts().get(0));
	}

	@Test
	public void testTryForceTerminatePreservesServletContext() throws InterruptedException {
		testTryForceTerminatePreservesContext(requestCtx);
	}

	@Test
	public void testTryForceTerminatePreservesWebsocketContext() throws InterruptedException {
		testTryForceTerminatePreservesContext(wsEventCtx);
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
