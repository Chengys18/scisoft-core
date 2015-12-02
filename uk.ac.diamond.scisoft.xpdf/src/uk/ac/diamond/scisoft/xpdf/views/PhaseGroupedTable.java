/*
 * Copyright 2015 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package uk.ac.diamond.scisoft.xpdf.views;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;

import org.eclipse.jface.viewers.CellEditor;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.ColumnViewer;
import org.eclipse.jface.viewers.EditingSupport;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.jface.viewers.TextCellEditor;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Layout;
import org.eclipse.swt.widgets.TableColumn;

/**
 * Display and edit phases for the XPDF project
 * @author Timothy Spain, timothy.spain@diamond.ac.uk
 *
 */
class PhaseGroupedTable {

	private List<XPDFPhase> phases;
	
	private XPDFGroupedTable groupedTable;

	private TreeSet<Integer> usedIDs;
	
	private SampleGroupedTable sampleTable;
	private List<XPDFPhase> visiblePhases;
	
	public PhaseGroupedTable(Composite parent, int style) {
		phases = new ArrayList<XPDFPhase>(Arrays.asList(new XPDFPhase[]{new XPDFPhase(), new XPDFPhase(), new XPDFPhase()}));
		
		String[] testNames = {"Quartz", "Crown glass", "Flint glass"}; 
		for (int i = 0; i < phases.size(); i++) {
			phases.get(i).setName(testNames[i]);
			phases.get(i).setId(getUniqueID());

		}
		groupedTable = new XPDFGroupedTable(parent, SWT.NONE);
		
		List<String> groupNames = new ArrayList<String>();
		List<ColumnInterface> columnInterfaces = new ArrayList<ColumnInterface>();
		List<List<ColumnInterface>> groupedColumnInterfaces = new ArrayList<List<ColumnInterface>>();
		
		groupNames.add("Phase Identification");
		columnInterfaces.add(new NameColumnInterface());
		columnInterfaces.add(new CodeColumnInterface());
		groupedColumnInterfaces.add(columnInterfaces);
		
		groupNames.add("");
		columnInterfaces = new ArrayList<ColumnInterface>();
		columnInterfaces.add(new CrystalColumnInterface());
		groupedColumnInterfaces.add(columnInterfaces);

		groupNames.add("Unit cell");
		columnInterfaces = new ArrayList<ColumnInterface>();
		columnInterfaces.add(new GroupColumnInterface());
		columnInterfaces.add(new UnitCellColumnInterface(0));
		columnInterfaces.add(new UnitCellColumnInterface(1));
		columnInterfaces.add(new UnitCellColumnInterface(2));
		columnInterfaces.add(new InternalAngleColumnInterface(0));
		columnInterfaces.add(new InternalAngleColumnInterface(1));
		columnInterfaces.add(new InternalAngleColumnInterface(2));
		groupedColumnInterfaces.add(columnInterfaces);

		groupNames.add("Properties");
		columnInterfaces = new ArrayList<ColumnInterface>();
		columnInterfaces.add(new CompositionColumnInterface());
		columnInterfaces.add(new DensityColumnInterface());
		groupedColumnInterfaces.add(columnInterfaces);
		
		groupNames.add("");
		columnInterfaces = new ArrayList<ColumnInterface>();
		columnInterfaces.add(new CommentColumnInterface());
		groupedColumnInterfaces.add(columnInterfaces);

		for (int iGroup = 0; iGroup < groupNames.size(); iGroup++) {
			groupedTable.createColumnGroup(groupNames.get(iGroup));
			for (int iColumn = 0; iColumn < groupedColumnInterfaces.get(iGroup).size(); iColumn++) {
				ColumnInterface colI = groupedColumnInterfaces.get(iGroup).get(iColumn);
				TableViewerColumn col = groupedTable.addColumn(groupNames.get(iGroup), SWT.NONE);
				col.getColumn().setText(colI.getName());
				groupedTable.setColumnWidth(col, colI.getWeight());
				col.setLabelProvider(colI.getLabelProvider());
				groupedTable.setColumnEditingSupport(col, colI);
				if (colI.getSelectionAdapter(this, col) != null) col.getColumn().addSelectionListener(colI.getSelectionAdapter(this, col));
			}
		}

		
		PhaseContentProvider contentProvider = new PhaseContentProvider();
		groupedTable.setContentProvider(contentProvider);
	}

 	// Generate a new id
	private	int getUniqueID() {
		final int lowestID = 2564;
		if (usedIDs == null)
			usedIDs = new TreeSet<Integer>();
		int theID = (usedIDs.isEmpty()) ? lowestID : usedIDs.last()+1;
		usedIDs.add(theID);
		return theID;
	}

	
	class PhaseContentProvider implements IStructuredContentProvider {

		@Override
		public void dispose() {
		}

		@Override
		public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
		}

		@Override
		public Object[] getElements(Object inputElement) {
			if (visiblePhases == null || visiblePhases.size() == 0)
				return phases.toArray(new XPDFPhase[]{});
			else
				return visiblePhases.toArray(new XPDFPhase[]{});
		}
		
	}
	
	/**
	 * Sets the input of the delegated viewer objects.
	 * @param input
	 * 				the object providing the input
	 */
	public void setInput(Object input) {
		groupedTable.setInput(input);
	}
	
	/**
	 * Sets the {@link Layout} data of the underlying Composite.
	 * @param layout
	 */
	public void setLayoutData(Object layout) {
		groupedTable.setLayoutData(layout);
	}

	public SelectionAdapter getColumnSelectionAdapter(final TableColumn tableColumn, final Comparator<XPDFPhase> comparator) {
		return new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				if (comparator == null) return;
				// Find the present sorted column, if any
				TableColumn presentSorted = null;
				int oldSortDirection = SWT.NONE;
				presentSorted = groupedTable.getSortColumn();
				oldSortDirection = groupedTable.getSortDirection();

				groupedTable.setSortColumn(null);
				groupedTable.setSortDirection(SWT.NONE);

				int newSortDirection = SWT.DOWN;
				
				// If the same column is sorted as is now selected, then reverse the sorting
				if (presentSorted == tableColumn)
					newSortDirection = (oldSortDirection == SWT.UP) ? SWT.DOWN : SWT.UP;

				// Do the sort
				Collections.sort(phases, comparator);
				if (newSortDirection == SWT.UP)
					Collections.reverse(phases);

				groupedTable.setSortColumn(tableColumn);
				groupedTable.setSortDirection(newSortDirection);

				groupedTable.refresh();
			}
		};
	}

	public void setSampleTable(SampleGroupedTable sampleTable) {
		this.sampleTable = sampleTable;
	}
	
	public void addPhases(List<XPDFPhase> addedPhases) {
		// String version
		for (XPDFPhase phase: addedPhases) {
			phase.setId(getUniqueID());
			phases.add(phase);
		}
		groupedTable.refresh();
	}
	
	public void setVisiblePhases(List<String> visiblePhases) {
		// String version
		
	}
	private interface ColumnInterface extends EditingSupportFactory {
		public SelectionAdapter getSelectionAdapter(final PhaseGroupedTable tab, final TableViewerColumn col);
		public ColumnLabelProvider getLabelProvider();
		public String getName();
		public int getWeight();
		public boolean presentAsUneditable(Object element);
	}
	
	static class DummyColumnInterface implements ColumnInterface {

		@Override
		public EditingSupport get(ColumnViewer v) {
			return new DummyEditingSupport(v);
		}

		@Override
		public SelectionAdapter getSelectionAdapter(PhaseGroupedTable tab,
				TableViewerColumn col) {
			return DummySelectionAdapter.get(tab, col);
		}

		@Override
		public ColumnLabelProvider getLabelProvider() {
			return new ColumnLabelProvider() {
				@Override
				public String getText(Object element) {
					return "This space left intentionally blank";
				}
			};
		}

		@Override
		public String getName() {
			return "Column";
		}

		@Override
		public int getWeight() {
			return 10;
		}

		@Override
		public boolean presentAsUneditable(Object element) {
			return false;
		}
		
	}
	
	static class DummyEditingSupport extends EditingSupport {
		DummyEditingSupport(ColumnViewer v) {
			super(v);
		}
		@Override
		protected CellEditor getCellEditor(Object element) {
			return null;
		}
		@Override
		protected boolean canEdit(Object element) {
			return false;
		}
		@Override
		protected Object getValue(Object element) {
			return null;
		}
		@Override
		protected void setValue(Object element, Object value) {
		}
	}

	static class DummySelectionAdapter {
		public static SelectionAdapter get(PhaseGroupedTable tab,
				TableViewerColumn col) {
			return tab.getColumnSelectionAdapter(col.getColumn(), new Comparator<XPDFPhase>() {
				@Override
				public int compare(XPDFPhase o1, XPDFPhase o2) {
					return 0;
				}
			});
		}
	}
	
	static class DummyLabelProvider extends ColumnLabelProvider {
		String text;
		public DummyLabelProvider(String text) {
			this.text = text;
		}
		@Override
		public String getText(Object element) {
			return text;
		}
	}
	
	static class NameColumnInterface implements ColumnInterface {

		@Override
		public EditingSupport get(final ColumnViewer v) {
			return new EditingSupport(v) {

				@Override
				protected CellEditor getCellEditor(Object element) {
					return new TextCellEditor(((TableViewer) v).getTable());
				}

				@Override
				protected boolean canEdit(Object element) {
					return true;
				}

				@Override
				protected Object getValue(Object element) {
					return (element != null) ? ((XPDFPhase) element).getName() : "";
				}

				@Override
				protected void setValue(Object element, Object value) {
					((XPDFPhase) element).setName( (value != null) ? (String) value : "");
					v.refresh();
				}
			};
		}

		@Override
		public SelectionAdapter getSelectionAdapter(final PhaseGroupedTable tab,
				final TableViewerColumn col) {
			return tab.getColumnSelectionAdapter(col.getColumn(), new Comparator<XPDFPhase>() {
				@Override
				public int compare(XPDFPhase o1, XPDFPhase o2) {
					return o1.getName().compareTo(o2.getName());
				}
			});
		}

		@Override
		public ColumnLabelProvider getLabelProvider() {
			return new ColumnLabelProvider() {
				@Override
				public String getText(Object element) {
					return ((XPDFPhase) element).getName();
				}
			};
		}

		@Override
		public String getName() {
			return "Name";
		}

		@Override
		public int getWeight() {
			return 20;
		}

		@Override
		public boolean presentAsUneditable(Object element) {
			return false;
		}
		
	}
	
	static class CodeColumnInterface implements ColumnInterface {

		@Override
		public EditingSupport get(ColumnViewer v) {
			return new DummyEditingSupport(v);
		}

		@Override
		public SelectionAdapter getSelectionAdapter(PhaseGroupedTable tab,
				TableViewerColumn col) {
			return tab.getColumnSelectionAdapter(col.getColumn(), new Comparator<XPDFPhase>() {
				@Override
				public int compare (XPDFPhase o1, XPDFPhase o2) {
					return Integer.compare(o1.getId(), o2.getId());
				}
			});
		}

		@Override
		public ColumnLabelProvider getLabelProvider() {
			return new ColumnLabelProvider() {
				@Override
				public String getText(Object element) {
					return "P"+String.format("%05d", ((XPDFPhase) element).getId());
				}
			};
		}

		@Override
		public String getName() {
			return "Code";
		}

		@Override
		public int getWeight() {
			return 5;
		}

		@Override
		public boolean presentAsUneditable(Object element) {
			return false;
		}
	}
	
	static class CrystalColumnInterface implements ColumnInterface {

		@Override
		public EditingSupport get(ColumnViewer v) {
			return new DummyEditingSupport(v);
		}

		@Override
		public SelectionAdapter getSelectionAdapter(PhaseGroupedTable tab,
				TableViewerColumn col) {
			return DummySelectionAdapter.get(tab, col);
		}

		@Override
		public ColumnLabelProvider getLabelProvider() {
			return new DummyLabelProvider("System");
		}

		@Override
		public String getName() {
			return "Crystal System";
		}

		@Override
		public int getWeight() {
			return 10;
		}

		@Override
		public boolean presentAsUneditable(Object element) {
			return false;
		}
	}

	static class GroupColumnInterface implements ColumnInterface {

		@Override
		public EditingSupport get(ColumnViewer v) {
			return new DummyEditingSupport(v);
		}

		@Override
		public SelectionAdapter getSelectionAdapter(PhaseGroupedTable tab,
				TableViewerColumn col) {
			return DummySelectionAdapter.get(tab, col);
		}

		@Override
		public ColumnLabelProvider getLabelProvider() {
			return new DummyLabelProvider("Group");
		}

		@Override
		public String getName() {
			return "Space Group";
		}

		@Override
		public int getWeight() {
			return 10;
		}

		@Override
		public boolean presentAsUneditable(Object element) {
			return false;
		}
		
	}
	
	static class UnitCellColumnInterface implements ColumnInterface {
		static final String[] axisNames = {"a", "b", "c"};
		int axisIndex;
		
		public UnitCellColumnInterface(int axisIndex) {
			this.axisIndex = axisIndex; 
		}

		@Override
		public EditingSupport get(ColumnViewer v) {
			return new DummyEditingSupport(v);
		}

		@Override
		public SelectionAdapter getSelectionAdapter(PhaseGroupedTable tab,
				TableViewerColumn col) {
			return DummySelectionAdapter.get(tab, col);
		}

		@Override
		public ColumnLabelProvider getLabelProvider() {
			return new DummyLabelProvider(axisNames[axisIndex]);
		}

		@Override
		public String getName() {
			return axisNames[axisIndex]+" (Å)";
		}

		@Override
		public int getWeight() {
			return 5;
		}

		@Override
		public boolean presentAsUneditable(Object element) {
			return false;
		}
	}
	
	static class InternalAngleColumnInterface implements ColumnInterface {
		static final String[] angleNames = {"α", "β", "γ"};
		int angleIndex;
		
		public InternalAngleColumnInterface(int angleIndex) {
			this.angleIndex = angleIndex;
		}

		@Override
		public EditingSupport get(ColumnViewer v) {
			return new DummyEditingSupport(v);
		}

		@Override
		public SelectionAdapter getSelectionAdapter(PhaseGroupedTable tab,
				TableViewerColumn col) {
			return DummySelectionAdapter.get(tab, col);
		}

		@Override
		public ColumnLabelProvider getLabelProvider() {
			return new DummyLabelProvider(angleNames[angleIndex]);
		}

		@Override
		public String getName() {
			return angleNames[angleIndex] + " (°)";
		}

		@Override
		public int getWeight() {
			return 5;
		}

		@Override
		public boolean presentAsUneditable(Object element) {
			return false;
		}
	}
	
	static class CompositionColumnInterface implements ColumnInterface {

		@Override
		public EditingSupport get(ColumnViewer v) {
			return new DummyEditingSupport(v);
		}

		@Override
		public SelectionAdapter getSelectionAdapter(PhaseGroupedTable tab,
				TableViewerColumn col) {
			return DummySelectionAdapter.get(tab, col);
		}

		@Override
		public ColumnLabelProvider getLabelProvider() {
			return new DummyLabelProvider("Elements!");
		}

		@Override
		public String getName() {
			return "Composition";
		}

		@Override
		public int getWeight() {
			return 20;
		}

		@Override
		public boolean presentAsUneditable(Object element) {
			return false;
		}
		
	}
	
	static class DensityColumnInterface implements ColumnInterface {

		@Override
		public EditingSupport get(ColumnViewer v) {
			return new DummyEditingSupport(v);
		}

		@Override
		public SelectionAdapter getSelectionAdapter(PhaseGroupedTable tab,
				TableViewerColumn col) {
			return DummySelectionAdapter.get(tab, col);
		}

		@Override
		public ColumnLabelProvider getLabelProvider() {
			return new DummyLabelProvider("Density");
		}

		@Override
		public String getName() {
			return "Density g cm⁻³";
		}

		@Override
		public int getWeight() {
			return 10;
		}

		@Override
		public boolean presentAsUneditable(Object element) {
			return false;
		}
		
	}
	
	static class CommentColumnInterface implements ColumnInterface {

		@Override
		public EditingSupport get(ColumnViewer v) {
			return new DummyEditingSupport(v);
		}

		@Override
		public SelectionAdapter getSelectionAdapter(PhaseGroupedTable tab,
				TableViewerColumn col) {
			return DummySelectionAdapter.get(tab, col);
		}

		@Override
		public ColumnLabelProvider getLabelProvider() {
			return new DummyLabelProvider("Words words words");
		}

		@Override
		public String getName() {
			return "Comment";
		}

		@Override
		public int getWeight() {
			return 30;
		}

		@Override
		public boolean presentAsUneditable(Object element) {
			return false;
		}
		
	}

}
