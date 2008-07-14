// Metawidget
//
// This library is free software; you can redistribute it and/or
// modify it under the terms of the GNU Lesser General Public
// License as published by the Free Software Foundation; either
// version 2.1 of the License, or (at your option) any later version.
//
// This library is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
// Lesser General Public License for more details.
//
// You should have received a copy of the GNU Lesser General Public
// License along with this library; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA

package org.metawidget.example.swing.tutorial;

import java.awt.event.ActionEvent;

import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JFrame;

import org.metawidget.inspector.propertytype.PropertyTypeInspector;
import org.metawidget.swing.SwingMetawidget;

/**
 * @author Richard Kennard
 */

public class Main
{
	public static void main( String[] args )
	{
		// Data model

		final Foo foo = new Foo();

		// Metawidget

		final SwingMetawidget metawidget = new SwingMetawidget();
		metawidget.setInspector( new PropertyTypeInspector() );
		metawidget.setToInspect( foo );

		if ( foo.bar == null )
		{
			final JButton button = new JButton();
			button.setAction( new AbstractAction( "init" ) {

				@Override
				public void actionPerformed( ActionEvent e )
				{
					foo.bar = new Bar();
					metawidget.remove( button );

					// TODO: why need to repaint?

					metawidget.repaint();
				}

			} );
			button.setName( "bar" );
			metawidget.add( button );
		}

		// JFrame

		JFrame frame = new JFrame( "Metawidget Tutorial" );
		frame.setDefaultCloseOperation( JFrame.EXIT_ON_CLOSE );
		frame.getContentPane().add( metawidget );
		frame.setSize( 400, 210 );
		frame.setVisible( true );
	}

	public static class Foo
	{
		public String name;

		public Bar bar;
	}

	public static class Bar
	{
		public String baz;
	}
}
