/*
 * Copyright (c) Piotr Morgwai Kotarbinski
 */
package pl.morgwai.base.servlet.scopes;

import pl.morgwai.base.guice.scopes.ThreadLocalContextTracker;
import pl.morgwai.base.guice.scopes.TrackableContext;



/**
 * blah
 */
public class InternalContextTracker<Ctx extends TrackableContext<Ctx>>
		extends ThreadLocalContextTracker<Ctx>{



	@Override
	protected void setCurrentContext(Ctx ctx) {
		super.setCurrentContext(ctx);
	}



	@Override
	protected void clearCurrentContext() {
		super.clearCurrentContext();
	}
}
