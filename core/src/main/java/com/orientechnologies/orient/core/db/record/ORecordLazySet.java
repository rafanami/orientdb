/*
 * Copyright 1999-2010 Luca Garulli (l.garulli--at--orientechnologies.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.orientechnologies.orient.core.db.record;

import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Set;

import com.orientechnologies.orient.core.id.ORID;
import com.orientechnologies.orient.core.record.ORecord;
import com.orientechnologies.orient.core.record.ORecordInternal;

/**
 * Lazy implementation of Set. Can be bound to a source ORecord object to keep track of changes. This avoid to call the makeDirty()
 * by hand when the set is changed.
 * <p>
 * <b>Internals</b>:
 * <ul>
 * <li>stores new records in a separate IdentityHashMap to keep underlying list (delegate) always ordered and minimizing sort
 * operations</li>
 * <li></li>
 * </ul>
 * 
 * </p>
 * 
 * @author Luca Garulli (l.garulli--at--orientechnologies.com)
 * 
 */
public class ORecordLazySet implements Set<OIdentifiable>, ORecordLazyMultiValue, ORecordElement {
	public static final ORecordLazySet					EMPTY_SET	= new ORecordLazySet((ODatabaseRecord) null);
	protected ORecordLazyList										delegate;
	protected IdentityHashMap<ORecord<?>, ORID>	newItems;
	private boolean															sorted		= true;

	public ORecordLazySet(final ODatabaseRecord iDatabase) {
		delegate = new ORecordLazyList(iDatabase);
	}

	public ORecordLazySet(final ORecordInternal<?> iSourceRecord) {
		delegate = new ORecordLazyList(iSourceRecord);
	}

	public void onBeforeIdentityChanged(final ORID iRID) {
		delegate.onBeforeIdentityChanged(iRID);
	}

	public void onAfterIdentityChanged(final ORecord<?> iRecord) {
		delegate.onAfterIdentityChanged(iRecord);
	}

	@SuppressWarnings("unchecked")
	public <RET> RET setDirty() {
		return (RET) delegate.setDirty();
	}

	public boolean setDatabase(final ODatabaseRecord iDatabase) {
		return delegate.setDatabase(iDatabase);
	}

	public Iterator<OIdentifiable> iterator() {
		if (newItems == null)
			return delegate.iterator();

		lazyLoad(false);
		return new OLazyRecordMultiIterator(delegate.sourceRecord, delegate.database, delegate.recordType, new Object[] {
				delegate.iterator(), newItems.keySet().iterator() }, delegate.autoConvertToRecord);
	}

	public Iterator<OIdentifiable> rawIterator() {
		if (newItems == null || newItems.size() == 0)
			return delegate.rawIterator();

		lazyLoad(false);
		return new OLazyRecordMultiIterator(delegate.sourceRecord, delegate.database, delegate.recordType, new Object[] {
				delegate.iterator(), newItems.keySet().iterator() }, false);
	}

	public void convertRecords2Links() {
		delegate.convertRecords2Links();
	}

	public boolean isAutoConvertToRecord() {
		return delegate.isAutoConvertToRecord();
	}

	public void setAutoConvertToRecord(final boolean convertToRecord) {
		delegate.setAutoConvertToRecord(convertToRecord);
	}

	public int size() {
		int tot = delegate.size();
		if (newItems != null)
			tot += newItems.size();
		return tot;
	}

	public boolean isEmpty() {
		boolean empty = delegate.isEmpty();

		if (empty && newItems != null)
			empty = newItems.isEmpty();

		return empty;
	}

	public boolean contains(final Object o) {
		lazyLoad(false);
		boolean found = Collections.binarySearch(delegate, (OIdentifiable) o) > -1;
		if (!found && newItems != null)
			// SEARCH INSIDE NEW ITEMS MAP
			found = newItems.containsKey(o);

		return found;
	}

	public Object[] toArray() {
		return delegate.toArray();
	}

	public <T> T[] toArray(final T[] a) {
		return delegate.toArray(a);
	}

	/**
	 * Adds the item in the underlying List preserving the order of the collection.
	 */
	public boolean add(final OIdentifiable e) {
		lazyLoad(true);
		return internalAdd(e);
	}

	public boolean remove(final Object o) {
		lazyLoad(true);

		final int pos = Collections.binarySearch(delegate, (OIdentifiable) o);
		if (pos > -1) {
			delegate.remove(pos);
			return true;
		}

		if (newItems != null)
			// SEARCH INSIDE NEW ITEMS MAP
			return newItems.remove(o) != null;

		return false;
	}

	public boolean containsAll(final Collection<?> c) {
		lazyLoad(false);
		return delegate.containsAll(c);
	}

	@SuppressWarnings("unchecked")
	public boolean addAll(Collection<? extends OIdentifiable> c) {
		final Iterator<OIdentifiable> it = (Iterator<OIdentifiable>) (c instanceof ORecordLazyMultiValue ? ((ORecordLazyMultiValue) c)
				.rawIterator() : c.iterator());

		while (it.hasNext())
			add(it.next());

		return true;
	}

	public boolean retainAll(final Collection<?> c) {
		return delegate.retainAll(c);
	}

	public boolean removeAll(final Collection<?> c) {
		return delegate.removeAll(c);
	}

	public void clear() {
		delegate.clear();
		if (newItems != null)
			newItems.clear();
	}

	public byte getRecordType() {
		return delegate.getRecordType();
	}

	@Override
	public String toString() {
		if (isLoaded()) {
			if (newItems == null || newItems.size() == 0)
				return ORecordMultiValueHelper.toString(this);
			else {
				final StringBuilder buffer = new StringBuilder(ORecordMultiValueHelper.toString(this));
				for (ORecord<?> item : newItems.keySet())
					buffer.insert(buffer.length() - 2, item.toString());
				return buffer.toString();
			}
		} else {
			return "NOT LOADED: " + getStreamedContent();
		}
	}

	public void resort() {
		if (!sorted && isLoaded()) {
			Collections.sort(delegate);
			sorted = true;
		}
	}

	public boolean isLoaded() {
		return delegate.isLoaded();
	}

	public ORecordLazySet setStreamedContent(final String iStream) {
		delegate.setStreamedContent(iStream);
		return this;
	}

	public String getStreamedContent() {
		return delegate.getStreamedContent();
	}

	protected boolean lazyLoad(final boolean iInvalidateStream) {
		return delegate.lazyLoad(iInvalidateStream);
	}

	public void convertLinks2Records() {
		delegate.convertLinks2Records();
	}

	/**
	 * Adds the item in the underlying List preserving the order of the collection.
	 */
	protected boolean internalAdd(final OIdentifiable e) {
		if (e.getIdentity().isNew()) {
			final ORecord<?> record = (ORecord<?>) e;
			// ADD IN TEMP LIST
			if (newItems == null)
				newItems = new IdentityHashMap<ORecord<?>, ORID>();
			newItems.put(record, record.getIdentity());
			return true;
		} else {
			final int pos = Collections.binarySearch(delegate, e);

			if (pos < 0) {
				// FOUND
				delegate.add(pos * -1 - 1, e);
				return true;
			}
			return false;
		}
	}

	public void savedAllNewItems() {
		if (newItems != null && newItems.size() > 0) {
			for (OIdentifiable item : newItems.keySet())
				add(item);
			newItems.clear();
		}
	}
}
