/* $RCSfile$
 * $Author: hansonr $
 * $Date: 2009-07-12 06:50:34 +0200 (dim., 12 juil. 2009) $
 * $Revision: 11203 $

 *
 * Copyright (C) 2002-2005  The Jmol Development Team
 *
 * Contact: jmol-developers@lists.sf.net
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 */

package org.jmol.shape;

import java.util.BitSet;

import org.jmol.g3d.Graphics3D;
import org.jmol.modelset.Atom;
import org.jmol.viewer.JmolConstants;

import java.util.Hashtable;

public class Balls extends AtomShape {
  
  public void setSize(int size, float fsize, BitSet bsSelected) {
    isActive = true;
    if (bsSizeSet == null)
      bsSizeSet = new BitSet();
    int bsLength = Math.min(atoms.length, bsSelected.length());
    for (int i = bsLength; --i >= 0; ) {
      if (bsSelected.get(i)) {
        Atom atom = atoms[i];
        atom.setMadAtom(viewer, size, fsize);
        bsSizeSet.set(i);
      }
    }
  }

  public void setProperty(String propertyName, Object value, BitSet bs) {
    if ("color" == propertyName) {
      short colix = Graphics3D.getColix(value);
      if (colix == Graphics3D.INHERIT_ALL)
        colix = Graphics3D.USE_PALETTE;
      if (bsColixSet == null)
        bsColixSet = new BitSet();
      byte pid = JmolConstants.pidOf(value);
      for (int i = atomCount; --i >= 0;)
        if (bs.get(i)) {
          Atom atom = atoms[i];
          atom.setColixAtom(setColix(colix, pid, atom));
          bsColixSet.set(i, colix != Graphics3D.USE_PALETTE
              || pid != JmolConstants.PALETTE_NONE);
          atom.setPaletteID(pid);
        }
      return;
    }
    if ("colorValues" == propertyName) {
      int[] values = (int[]) value;
      if (values.length == 0)
        return;
      if (bsColixSet == null)
        bsColixSet = new BitSet();
      int n = 0;
      Integer color = null;
      for (int i = 0; i < atomCount; i++)
        if (bs.get(i)) {
          if (n >= values.length)
            return;
          color = new Integer(values[n++]);
          short colix = Graphics3D.getColix(color);
          if (colix == Graphics3D.INHERIT_ALL)
            colix = Graphics3D.USE_PALETTE;
          byte pid = JmolConstants.pidOf(color);
          Atom atom = atoms[i];
          atom.setColixAtom(setColix(colix, pid, atom));
          bsColixSet.set(i, colix != Graphics3D.USE_PALETTE
              || pid != JmolConstants.PALETTE_NONE);
          atom.setPaletteID(pid);
        }
      return;
    }
    if ("translucency" == propertyName) {
      boolean isTranslucent = (((String)value).equals("translucent"));
      if (bsColixSet == null)
        bsColixSet = new BitSet();
      for (int i = atomCount; --i >= 0;)
        if (bs.get(i)) {
          atoms[i].setTranslucent(isTranslucent, translucentLevel);
          if (isTranslucent)
            bsColixSet.set(i);
        }
      return;
    }
    super.setProperty(propertyName, value, bs);
 }

 public void setModelClickability() {
   BitSet bs = viewer.getDeletedAtoms();
   if (bs == null)
     bs = new BitSet();
   for (int i = atomCount; --i >= 0;) {
     Atom atom = atoms[i];
     atom.setClickable(0);
     if (bs.get(i) || (atom.getShapeVisibilityFlags() & myVisibilityFlag) == 0
         || modelSet.isAtomHidden(i))
       continue;
     atom.setClickable(myVisibilityFlag);
   }
 }
  
 public void setVisibilityFlags(BitSet bs) {
    int displayModelIndex = viewer.getDisplayModelIndex();
    boolean isOneFrame = (displayModelIndex >= 0); 
    boolean showHydrogens = viewer.getShowHydrogens();
    BitSet bsDeleted = viewer.getDeletedAtoms();
    if (bsDeleted == null)
      bsDeleted = new BitSet();
    for (int i = atomCount; --i >= 0; ) {
      Atom atom = atoms[i];
      int flag = atom.getShapeVisibilityFlags();
      flag &= (~JmolConstants.ATOM_IN_FRAME & ~myVisibilityFlag);
      atom.setShapeVisibilityFlags(flag);
      if (bsDeleted.get(i) || !showHydrogens && atom.getElementNumber() == 1)
        continue;
      int modelIndex = atom.getModelIndex();
      if (!isOneFrame && bs.get(modelIndex) 
          || modelIndex == displayModelIndex) { 
        atom.setShapeVisibility(JmolConstants.ATOM_IN_FRAME, true);
        if (atom.getMadAtom() != 0 &&  !modelSet.isAtomHidden(i))
          atom.setShapeVisibility(myVisibilityFlag, true);
      }
    }
  }

 public String getShapeState() {
    Hashtable temp = new Hashtable();
    float r = 0;
    for (int i = 0; i < atomCount; i++) {
      if (bsSizeSet != null && bsSizeSet.get(i)) {
        if ((r = atoms[i].getMadAtom()) < 0)
          setStateInfo(temp, i, "Spacefill on");
        else
          setStateInfo(temp, i, "Spacefill " + (r / 2000f));
      }
      if (bsColixSet != null && bsColixSet.get(i)) {
        byte pid = atoms[i].getPaletteID();
        if (pid != JmolConstants.PALETTE_CPK || atoms[i].isTranslucent())
          setStateInfo(temp, i, getColorCommand("atoms", pid, atoms[i].getColix()));
      }
    }
    return getShapeCommands(temp, null, atomCount);
  }
  
  /*
  boolean checkObjectHovered(int x, int y) {
    //just for debugging
    if (!viewer.getNavigationMode())
      return false;
    viewer.hoverOn(x, y, x + " " + y);
    return true;
  }
  */

}
