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

import android.view.LayoutInflater;
import android.view.ViewGroup;

import org.kontalk.data.Contact;
import org.kontalk.message.ImageComponent;
import org.kontalk.message.MessageComponent;
import org.kontalk.message.RawComponent;
import org.kontalk.message.TextComponent;

import java.util.regex.Pattern;


/**
 * Factory for building {@link MessageContentView}s out of
 * {@link MessageComponent}s.
 * @author Daniele Ricci
 */
public class MessageContentViewFactory {

    private MessageContentViewFactory() {
    }

    /** Builds the content for the given component. */
    @SuppressWarnings("unchecked")
    public static <T> MessageContentView<T> createContent(LayoutInflater inflater,
            ViewGroup parent, T component,
            Contact contact, Pattern highlight) {

        // using conditionals to avoid reflection
        MessageContentView<T> view = null;

        if (component instanceof TextComponent) {
            view = (MessageContentView<T>) TextContentView.obtain(inflater, parent);
        }
        else if (component instanceof ImageComponent) {
            view = (MessageContentView<T>) ImageContentView.create(inflater, parent);
        }

        if (view != null)
            view.bind(component, contact, highlight);

        return view;
    }

}
