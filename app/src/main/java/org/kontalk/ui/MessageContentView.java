/*
 * Kontalk Android client
 * Copyright (C) 2014 Kontalk Devteam <devteam@kontalk.org>

 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.kontalk.ui;

import org.kontalk.data.Contact;

import java.util.regex.Pattern;


/**
 * Interface for message content views for all message component types.
 * @author Daniele Ricci
 */
public interface MessageContentView<T> {

    /** Binds the given component with this view. */
    public void bind(T component, Contact contact, Pattern highlight);

    /** Unbinds and release all resources from this view. */
    public void unbind();

    /** Returns the component associated with this view. */
    public T getComponent();

    /** Returns the priority of the view. Lower values means higher position. */
    public int getPriority();

    // TODO

}
