package simplegenomehub.util.MultipleSynteny;

import simplegenomehub.model.SpeciesInfo;

import javax.swing.JComponent;
import javax.swing.JTree;
import javax.swing.TransferHandler;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.awt.Component;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDropEvent;
import java.io.IOException;

public final class MultipleSyntenySpeciesDragDropSupport {

    private static final DataFlavor SPECIES_FLAVOR = createSpeciesFlavor();

    private MultipleSyntenySpeciesDragDropSupport() {
    }

    public static void installSpeciesDragSource(JTree tree) {
        if (tree == null) {
            return;
        }

        tree.setDragEnabled(true);
        tree.setTransferHandler(new TransferHandler() {
            @Override
            protected Transferable createTransferable(JComponent component) {
                SpeciesInfo species = getSelectedSpecies(tree);
                return species == null ? null : new SpeciesTransferable(species);
            }

            @Override
            public int getSourceActions(JComponent component) {
                return COPY;
            }
        });
    }

    public static void installSpeciesDropTarget(Component target,
                                                java.util.function.Consumer<SpeciesInfo> dropConsumer) {
        if (target == null || dropConsumer == null) {
            return;
        }

        new DropTarget(target, DnDConstants.ACTION_COPY, new DropTargetAdapter() {
            @Override
            public void drop(DropTargetDropEvent event) {
                try {
                    if (!event.isDataFlavorSupported(SPECIES_FLAVOR)) {
                        event.rejectDrop();
                        event.dropComplete(false);
                        return;
                    }

                    event.acceptDrop(DnDConstants.ACTION_COPY);
                    Transferable transferable = event.getTransferable();
                    SpeciesInfo species = (SpeciesInfo) transferable.getTransferData(SPECIES_FLAVOR);
                    dropConsumer.accept(species);
                    event.dropComplete(true);
                } catch (UnsupportedFlavorException | IOException ex) {
                    event.dropComplete(false);
                } catch (Exception ex) {
                    event.dropComplete(false);
                }
            }
        }, true);
    }

    private static SpeciesInfo getSelectedSpecies(JTree tree) {
        if (tree == null) {
            return null;
        }

        TreePath selectionPath = tree.getSelectionPath();
        if (selectionPath == null) {
            return null;
        }

        Object node = selectionPath.getLastPathComponent();
        if (node instanceof DefaultMutableTreeNode) {
            Object userObject = ((DefaultMutableTreeNode) node).getUserObject();
            if (userObject instanceof SpeciesInfo) {
                return (SpeciesInfo) userObject;
            }
        }
        return null;
    }

    private static DataFlavor createSpeciesFlavor() {
        return new DataFlavor(
            DataFlavor.javaJVMLocalObjectMimeType + ";class=" + SpeciesInfo.class.getName(),
            "SpeciesInfo"
        );
    }

    private static final class SpeciesTransferable implements Transferable {
        private final SpeciesInfo species;

        private SpeciesTransferable(SpeciesInfo species) {
            this.species = species;
        }

        @Override
        public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[] { SPECIES_FLAVOR };
        }

        @Override
        public boolean isDataFlavorSupported(DataFlavor flavor) {
            return SPECIES_FLAVOR.equals(flavor);
        }

        @Override
        public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
            if (!isDataFlavorSupported(flavor)) {
                throw new UnsupportedFlavorException(flavor);
            }
            return species;
        }
    }
}
