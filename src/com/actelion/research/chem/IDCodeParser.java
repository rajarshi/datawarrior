/*
 * Copyright 2014 Actelion Pharmaceuticals Ltd., Gewerbestrasse 16, CH-4123 Allschwil, Switzerland
 *
 * This file is part of DataWarrior.
 * 
 * DataWarrior is free software: you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 * 
 * DataWarrior is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with DataWarrior.
 * If not, see http://www.gnu.org/licenses/.
 *
 * @author Thomas Sander
 */

package com.actelion.research.chem;

public class IDCodeParser {
	private StereoMolecule mMol;
	private byte[]		mDecodingBytes;
	private	int			mIDCodeBitsAvail,mIDCodeTempData,mIDCodeBufferIndex;
	private boolean		mEnsure2DCoordinates;

	/**
	 * This default constructor creates molecules guaranteed to have 2D-atom-coordinates.
	 * If 2D-coordinates are not supplied with the idcode, or if supplied coordinates are 3D,
	 * then new 2D-coordinates are created on the fly.
	 */
	public IDCodeParser() {
		this(true);
		}

	/**
	 * 
	 * @param ensure2DCoordinates If TRUE and no coordinates are passed with the idcode, then
	 * the parser generates atom coordinates of any molecule and assigns up/down bonds reflecting
	 * given atom parities. Generating coordinates is potentially error prone, such that providing
	 * original coordinates, where available, should be the preferred option.
	 * <br><b>WARNING:</b> If FALSE: In this case stereo parities are taken directly from the idcode,
	 * missing explicitly 'unknown' parities, because they are not part of the idcode.
	 * Without atom coordinates up/down bonds cannot be assigned. If further processing relies
	 * on up/down bond stereo information or needs to distinguish parities 'none' from 'unknown',
	 * (e.g. idcode creation, checking for stereo centers, calculating the skeletonSpheres descriptor),
	 * or if you are not exactly sure, what to do, then use the constructor IDCodeParser(true).
	 * If you supply encoded 3D-coordinates, then use IDCodeParser(false).
	 */
	public IDCodeParser(boolean ensure2DCoordinates) {
		mEnsure2DCoordinates = ensure2DCoordinates;
		}

	public StereoMolecule getCompactMolecule(String idcode) {
		return (idcode == null || idcode.length() == 0) ? null : getCompactMolecule(idcode.getBytes(), null);
		}

	public StereoMolecule getCompactMolecule(byte[] idcode) {
		return getCompactMolecule(idcode, null);
		}

	public StereoMolecule getCompactMolecule(String idcode, String coordinates) {
		return (idcode == null) ? null : getCompactMolecule(idcode.getBytes(),
							(coordinates == null) ? null : coordinates.getBytes());
		}

	public StereoMolecule getCompactMolecule(byte[] idcode, byte[] coordinates) {
		if (idcode == null)
			return null;

		decodeBitsStart(idcode, 0);
		int abits = decodeBits(4);
		int bbits = decodeBits(4);

		if (abits > 8)	// abits is the version number
			abits = bbits;

		int allAtoms = decodeBits(abits);
		int allBonds = decodeBits(bbits);

		StereoMolecule mol = new StereoMolecule(allAtoms, allBonds);
		parse(mol, idcode, coordinates);
		return mol;
		}

	public void parse(StereoMolecule mol, String idcode) {
		parse(mol, (idcode == null) ? null : idcode.getBytes(), null);
		}

	public void parse(StereoMolecule mol, byte[] idcode) {
		parse(mol, idcode, null);
		}

	public void parse(StereoMolecule mol, String idcode, String coordinates) {
		byte[] idcodeBytes = (idcode == null) ? null : idcode.getBytes();
		byte[] coordinateBytes = (coordinates == null) ? null : coordinates.getBytes();
		parse(mol, idcodeBytes, coordinateBytes);
		}

	public void parse(StereoMolecule mol, byte[] idcode, byte[] coordinates) {
		int version = Canonizer.cIDCodeVersion2;
		mMol = mol;
		mMol.deleteMolecule();

		if (idcode==null || idcode.length==0)
			return;

		if (coordinates != null && coordinates.length == 0)
			coordinates = null;

		decodeBitsStart(idcode, 0);
		int abits = decodeBits(4);
		int bbits = decodeBits(4);

		if (abits > 8) {	// abits is the version number
			version = abits;
			abits = bbits;
			}

		if (abits == 0) {
			mMol.setFragment((decodeBits(1) == 1) ? true : false);
			return;
			}

		int allAtoms = decodeBits(abits);
		int allBonds = decodeBits(bbits);
		int nitrogens = decodeBits(abits);
		int oxygens = decodeBits(abits);
		int otherAtoms = decodeBits(abits);
		int chargedAtoms = decodeBits(abits);
		for (int atom=0; atom<allAtoms; atom++)
			mMol.addAtom(6);
		for (int i=0; i<nitrogens; i++)
			mMol.setAtomicNo(decodeBits(abits), 7);
		for (int i=0; i<oxygens; i++)
			mMol.setAtomicNo(decodeBits(abits), 8);
		for (int i=0; i<otherAtoms; i++)
			mMol.setAtomicNo(decodeBits(abits),
							 decodeBits(8));
		for (int i=0; i<chargedAtoms; i++)
			mMol.setAtomCharge(decodeBits(abits),
							   decodeBits(4) - 8);

		int closureBonds = 1 + allBonds - allAtoms;
		int dbits = decodeBits(4);
		int base = 0;

		mMol.setAtomX(0, 0.0);
		mMol.setAtomY(0, 0.0);
		mMol.setAtomZ(0, 0.0);

		boolean decodeOldCoordinates = (coordinates != null && coordinates[0] >= '\'');
		float targetAVBL = 0.0f;
		float xOffset = 0.0f;
		float yOffset = 0.0f;
		float zOffset = 0.0f;
		boolean coordsAre3D = false;
		boolean coordsAreAbsolute = false;

		if (decodeOldCoordinates) {	// old coordinate encoding
			if ((coordinates.length > 2*allAtoms-2 && coordinates[2*allAtoms-2] == '\'')
			 || (coordinates.length > 3*allAtoms-3 && coordinates[3*allAtoms-3] == '\'')) {	// old faulty encoding
				coordsAreAbsolute = true;
				coordsAre3D = (coordinates.length == 3*allAtoms-3+9);
				int index = coordsAre3D ? 3*allAtoms-3 : 2*allAtoms-2;
				int avblInt = 86*((int)coordinates[index+1]-40)+(int)coordinates[index+2]-40;
				targetAVBL = (float)Math.pow(10.0, avblInt/2000.0-1.0);
				index += 2;
				int xInt = 86*((int)coordinates[index+1]-40)+(int)coordinates[index+2]-40;
				xOffset = (float)Math.pow(10.0, xInt/1500.0-1.0);
				index += 2;
				int yInt = 86*((int)coordinates[index+1]-40)+(int)coordinates[index+2]-40;
				yOffset = (float)Math.pow(10.0, yInt/1500.0-1.0);
				if (coordsAre3D) {
					index += 2;
					int zInt = 86*((int)coordinates[index+1]-40)+(int)coordinates[index+2]-40;
					zOffset = (float)Math.pow(10.0, zInt/1500.0-1.0);
					}
				}
			else {
				coordsAre3D = (coordinates.length == 3*allAtoms-3);
				}
			}

		// don't use 3D coordinates, if we need 2D
		if (mEnsure2DCoordinates && coordsAre3D) {
			coordinates = null;
			decodeOldCoordinates = false;
			}

		for (int i=1; i<allAtoms; i++) {
			int dif = decodeBits(dbits);
			if (dif == 0) {
				if (decodeOldCoordinates) {
					mMol.setAtomX(i, mMol.getAtomX(0) + 8 * (coordinates[i*2-2]-83));
					mMol.setAtomY(i, mMol.getAtomY(0) + 8 * (coordinates[i*2-1]-83));
					if (coordsAre3D)
						mMol.setAtomZ(i, mMol.getAtomZ(0) + 8 * (coordinates[2*allAtoms-3+i]-83));
					}

				closureBonds++;
				continue;
				}

			base += dif - 1;

			if (decodeOldCoordinates) {
				mMol.setAtomX(i, mMol.getAtomX(base) + coordinates[i*2-2] - 83);
				mMol.setAtomY(i, mMol.getAtomY(base) + coordinates[i*2-1] - 83);
				if (coordsAre3D)
					mMol.setAtomZ(i, mMol.getAtomZ(base) + (coordinates[2*allAtoms-3+i]-83));
				}
			mMol.addBond(base, i, Molecule.cBondTypeSingle);
			}

		for (int i=0; i<closureBonds; i++)
			mMol.addBond(decodeBits(abits),
						 decodeBits(abits), Molecule.cBondTypeSingle);

		boolean[] isAromaticBond = new boolean[allBonds];

		for (int bond=0; bond<allBonds; bond++) {
			int bondOrder = decodeBits(2);
			switch (bondOrder) {
			case 0:
				isAromaticBond[bond] = true;
				break;
			case 2:
				mMol.setBondType(bond, Molecule.cBondTypeDouble);
				break;
			case 3:
				mMol.setBondType(bond, Molecule.cBondTypeTriple);
				break;
				}
			}

		int THCount = decodeBits(abits);
		for (int i=0; i<THCount; i++) {
			int atom = decodeBits(abits);
			if (version == Canonizer.cIDCodeVersion2) {
				int parity = decodeBits(2);
				if (parity == 3) {
					// this was the old discontinued Molecule.cAtomParityMix
					// version2 idcodes had never more than one center with parityMix
					mMol.setAtomESR(atom, Molecule.cESRTypeAnd, 0);
					mMol.setAtomParity(atom, Molecule.cAtomParity1, false);
					}
				else {
					mMol.setAtomParity(atom, parity, false);
					}
				}
			else {
				int parity = decodeBits(3);
				switch (parity) {
				case Canonizer.cParity1And:
					mMol.setAtomParity(atom, Molecule.cAtomParity1, false);
					mMol.setAtomESR(atom, Molecule.cESRTypeAnd, decodeBits(3));
					break;
				case Canonizer.cParity2And:
					mMol.setAtomParity(atom, Molecule.cAtomParity2, false);
					mMol.setAtomESR(atom, Molecule.cESRTypeAnd, decodeBits(3));
					break;
				case Canonizer.cParity1Or:
					mMol.setAtomParity(atom, Molecule.cAtomParity1, false);
					mMol.setAtomESR(atom, Molecule.cESRTypeOr, decodeBits(3));
					break;
				case Canonizer.cParity2Or:
					mMol.setAtomParity(atom, Molecule.cAtomParity2, false);
					mMol.setAtomESR(atom, Molecule.cESRTypeOr, decodeBits(3));
					break;
				default:
					mMol.setAtomParity(atom, parity, false);
					}
				}
			}

		if (version == Canonizer.cIDCodeVersion2)
			if ((decodeBits(1) == 0))   // translate chiral flag
				mMol.setToRacemate();

		int EZCount = decodeBits(bbits);
		for (int i=0; i<EZCount; i++) {
			int bond = decodeBits(bbits);
			if (mMol.getBondType(bond) == Molecule.cBondTypeSingle) {	// BINAP type of axial chirality
				int parity = decodeBits(3);
				switch (parity) {
				case Canonizer.cParity1And:
					mMol.setBondParity(bond, Molecule.cBondParityEor1, false);
					mMol.setBondESR(bond, Molecule.cESRTypeAnd, decodeBits(3));
					break;
				case Canonizer.cParity2And:
					mMol.setBondParity(bond, Molecule.cBondParityZor2, false);
					mMol.setBondESR(bond, Molecule.cESRTypeAnd, decodeBits(3));
					break;
				case Canonizer.cParity1Or:
					mMol.setBondParity(bond, Molecule.cBondParityEor1, false);
					mMol.setBondESR(bond, Molecule.cESRTypeOr, decodeBits(3));
					break;
				case Canonizer.cParity2Or:
					mMol.setBondParity(bond, Molecule.cBondParityZor2, false);
					mMol.setBondESR(bond, Molecule.cESRTypeOr, decodeBits(3));
					break;
				default:
					mMol.setBondParity(bond, parity, false);
					}
				}
			else {
				mMol.setBondParity(bond, decodeBits(2), false);	// double bond
				}
			}

		mMol.setFragment((decodeBits(1) == 1) ? true : false);

		int[] aromaticSPBond = null;

		int offset = 0;
		while (decodeBits(1) == 1) {
			int dataType = offset + decodeBits(4);
			switch (dataType) {
			case 0:	//	datatype 'AtomQFNoMoreNeighbours'
				int no = decodeBits(abits);
				for (int i=0; i<no; i++) {
					int atom = decodeBits(abits);
					mMol.setAtomQueryFeature(atom, Molecule.cAtomQFNoMoreNeighbours, true);
					}
				break;
			case 1:	//	datatype 'isotop'
				no = decodeBits(abits);
				for (int i=0; i<no; i++) {
					int atom = decodeBits(abits);
					int mass = decodeBits(8);
					mMol.setAtomMass(atom, mass);
					}
				break;
			case 2:	//	datatype 'bond defined to be delocalized'
				no = decodeBits(bbits);
				for (int i=0; i<no; i++) {
					int bond = decodeBits(bbits);
					mMol.setBondType(bond, Molecule.cBondTypeDelocalized);
					}
				break;
			case 3:	//	datatype 'AtomQFMoreNeighbours'
				no = decodeBits(abits);
				for (int i=0; i<no; i++) {
					int atom = decodeBits(abits);
					mMol.setAtomQueryFeature(atom, Molecule.cAtomQFMoreNeighbours, true);
					}
				break;
			case 4:	//	datatype 'AtomQFRingState'
				no = decodeBits(abits);
				for (int i=0; i<no; i++) {
					int atom = decodeBits(abits);
					int ringState = decodeBits(Molecule.cAtomQFRingStateBits) << Molecule.cAtomQFRingStateShift;
					mMol.setAtomQueryFeature(atom, ringState, true);
					}
				break;
			case 5:	//	datatype 'AtomQFAromState'
				no = decodeBits(abits);
				for (int i=0; i<no; i++) {
					int atom = decodeBits(abits);
					int aromState = decodeBits(Molecule.cAtomQFAromStateBits) << Molecule.cAtomQFAromStateShift;
					mMol.setAtomQueryFeature(atom, aromState, true);
					}
				break;
			case 6:	//	datatype 'AtomQFAny'
				no = decodeBits(abits);
				for (int i=0; i<no; i++) {
					int atom = decodeBits(abits);
					mMol.setAtomQueryFeature(atom, Molecule.cAtomQFAny, true);
					}
				break;
			case 7:	//	datatype 'AtomQFHydrogen'
				no = decodeBits(abits);
				for (int i=0; i<no; i++) {
					int atom = decodeBits(abits);
					int hydrogen = decodeBits(Molecule.cAtomQFHydrogenBits) << Molecule.cAtomQFHydrogenShift;
					mMol.setAtomQueryFeature(atom, hydrogen, true);
					}
				break;
			case 8:	//	datatype 'AtomList'
				no = decodeBits(abits);
				for (int i=0; i<no; i++) {
					int atom = decodeBits(abits);
					int atoms = decodeBits(4);
					int[] atomList = new int[atoms];
					for (int j=0; j<atoms; j++) {
						int atomicNo = decodeBits(8);
						atomList[j] = atomicNo;
						}
					mMol.setAtomList(atom, atomList);
					}
				break;
			case 9:	//	datatype 'BondQFRingState'
				no = decodeBits(bbits);
				for (int i=0; i<no; i++) {
					int bond = decodeBits(bbits);
					int ringState = decodeBits(Molecule.cBondQFRingStateBits) << Molecule.cBondQFRingStateShift;
					mMol.setBondQueryFeature(bond, ringState, true);
					}
				break;
			case 10://	datatype 'BondQFBondTypes'
				no = decodeBits(bbits);
				for (int i=0; i<no; i++) {
					int bond = decodeBits(bbits);
					int bondTypes = decodeBits(Molecule.cBondQFBondTypesBits) << Molecule.cBondQFBondTypesShift;
					mMol.setBondQueryFeature(bond, bondTypes, true);
					}
				break;
			case 11:	//	datatype 'AtomQFMatchStereo'
				no = decodeBits(abits);
				for (int i=0; i<no; i++) {
					int atom = decodeBits(abits);
					mMol.setAtomQueryFeature(atom, Molecule.cAtomQFMatchStereo, true);
					}
				break;
			case 12:	//  datatype 'bond defined to be a bridge from n1 to n2 atoms'
				no = decodeBits(bbits);
				for (int i=0; i<no; i++) {
					int bond = decodeBits(bbits);
					int bridgeData = decodeBits(Molecule.cBondQFBridgeBits) << Molecule.cBondQFBridgeShift;
					mMol.setBondQueryFeature(bond, bridgeData, true);
					}
				break;
			case 13: //  datatype 'AtomQFPiElectrons'
				no = decodeBits(abits);
				for (int i=0; i<no; i++) {
					int atom = decodeBits(abits);
					int piElectrons = decodeBits(Molecule.cAtomQFPiElectronBits) << Molecule.cAtomQFPiElectronShift;
					mMol.setAtomQueryFeature(atom, piElectrons, true);
					}
				break;
			case 14: //  datatype 'AtomQFNeighbours'
				no = decodeBits(abits);
				for (int i=0; i<no; i++) {
					int atom = decodeBits(abits);
					int neighbours = decodeBits(Molecule.cAtomQFNeighbourBits) << Molecule.cAtomQFNeighbourShift;
					mMol.setAtomQueryFeature(atom, neighbours, true);
					}
				break;
			case 15: //  datatype 'start second feature set'
				offset = 16;
				break;
			case 16: //  datatype 'AtomQFRingSize'
				no = decodeBits(abits);
				for (int i=0; i<no; i++) {
					int atom = decodeBits(abits);
					int ringSize = decodeBits(Molecule.cAtomQFRingSizeBits) << Molecule.cAtomQFRingSizeShift;
					mMol.setAtomQueryFeature(atom, ringSize, true);
					}
				break;
			case 17: //  datatype 'AtomAbnormalValence'
				no = decodeBits(abits);
				for (int i=0; i<no; i++) {
					int atom = decodeBits(abits);
					mMol.setAtomAbnormalValence(atom, decodeBits(4));
					}
				break;
			case 18: //  datatype 'AtomCustomLabel'
				no = decodeBits(abits);
				int lbits = decodeBits(4);
				for (int i=0; i<no; i++) {
					int atom = decodeBits(abits);
					int count = decodeBits(lbits);
					byte[] label = new byte[count];
					for (int j=0; j<count; j++)
						label[j] = (byte)decodeBits(7);
					mMol.setAtomCustomLabel(atom, new String(label));
					}
				break;
			case 19: //  datatype 'AtomQFCharge'
				no = decodeBits(abits);
				for (int i=0; i<no; i++) {
					int atom = decodeBits(abits);
					int charge = decodeBits(Molecule.cAtomQFChargeBits) << Molecule.cAtomQFChargeShift;
					mMol.setAtomQueryFeature(atom, charge, true);
					}
				break;
			case 20: //  datatype 'BondQFRingSize'
				no = decodeBits(bbits);
				for (int i=0; i<no; i++) {
					int bond = decodeBits(bbits);
					int ringSize = decodeBits(Molecule.cBondQFRingSizeBits) << Molecule.cBondQFRingSizeShift;
					mMol.setBondQueryFeature(bond, ringSize, true);
					}
				break;
			case 21: //  datatype 'AtomRadicalState'
				no = decodeBits(abits);
				for (int i=0; i<no; i++) {
					int atom = decodeBits(abits);
					mMol.setAtomRadical(atom, decodeBits(2) << Molecule.cAtomRadicalStateShift);
					}
				break;
			case 22:	//	datatype 'flat nitrogen'
				no = decodeBits(abits);
				for (int i=0; i<no; i++) {
					int atom = decodeBits(abits);
					mMol.setAtomQueryFeature(atom, Molecule.cAtomQFFlatNitrogen, true);
					}
				break;
			case 23:	//	datatype 'BondQFMatchStereo'
				no = decodeBits(bbits);
				for (int i=0; i<no; i++) {
					int bond = decodeBits(bbits);
					mMol.setBondQueryFeature(bond, Molecule.cBondQFMatchStereo, true);
					}
				break;
			case 24:	//	datatype 'cBondQFAromState'
				no = decodeBits(bbits);
				for (int i=0; i<no; i++) {
					int bond = decodeBits(bbits);
					int aromState = decodeBits(Molecule.cBondQFAromStateBits) << Molecule.cBondQFAromStateShift;
					mMol.setBondQueryFeature(bond, aromState, true);
					}
				break;
			case 25:	//	datatype 'atom selection'
				for (int i=0; i<allAtoms; i++)
					if (decodeBits(1) == 1)
						mMol.setAtomSelection(i, true);
				break;
			case 26:	//	datatype 'delocalized high order bond'
				no = decodeBits(bbits);
				aromaticSPBond = new int[no];
				for (int i=0; i<no; i++)
					aromaticSPBond[i] = decodeBits(bbits);
				break;
				}
			}

		if (coordinates != null) {
			if (coordinates[0] == '!') {	// new coordinate format
				decodeBitsStart(coordinates, 1);
				coordsAre3D = (decodeBits(1) == 1);
				coordsAreAbsolute = (decodeBits(1) == 1);
				int resolutionBits = 2 * decodeBits(4);
				int binCount = (1 << resolutionBits);
	
				float factor = 0.0f;
				int from = 0;
				int bond = 0;
				for (int atom=1; atom<allAtoms; atom++) {
					if (bond<allBonds && mMol.getBondAtom(1, bond) == atom) {
						from = mMol.getBondAtom(0, bond++);
						factor = 1.0f;
						}
					else {
						from = 0;
						factor = 8.0f;
						}
					mMol.setAtomX(atom, mMol.getAtomX(from) + factor * (decodeBits(resolutionBits) - binCount/2));
					mMol.setAtomY(atom, mMol.getAtomY(from) + factor * (decodeBits(resolutionBits) - binCount/2));
					if (coordsAre3D)
						mMol.setAtomZ(atom, mMol.getAtomZ(from) + factor * (decodeBits(resolutionBits) - binCount/2));
					}
	
				if (coordsAreAbsolute) {
					targetAVBL = decodeAVBL(decodeBits(resolutionBits), binCount);
					xOffset = decodeShift(decodeBits(resolutionBits), binCount);
					yOffset = decodeShift(decodeBits(resolutionBits), binCount);
					if (coordsAre3D)
						zOffset = decodeShift(decodeBits(resolutionBits), binCount);
	
					factor = targetAVBL / mMol.getAverageBondLength(true);
					for (int atom=0; atom<allAtoms; atom++) {
						mMol.setAtomX(atom, xOffset + factor * mMol.getAtomX(atom));
						mMol.setAtomY(atom, yOffset + factor * mMol.getAtomY(atom));
						if (coordsAre3D)
							mMol.setAtomZ(atom, zOffset + factor * mMol.getAtomZ(atom));
						}
					}
				else {	// with new format 2D and 3D coordinates are scaled to average bond lengths of 1.5 Angstrom
					targetAVBL = 1.5f;
					factor = targetAVBL / mMol.getAverageBondLength(true);
					for (int atom=0; atom<allAtoms; atom++) {
						mMol.setAtomX(atom, factor * mMol.getAtomX(atom));
						mMol.setAtomY(atom, factor * mMol.getAtomY(atom));
						if (coordsAre3D)
							mMol.setAtomZ(atom, factor * mMol.getAtomZ(atom));
						}
					}
				}
			else {	// old coordinate format
				if (coordsAre3D && !coordsAreAbsolute && targetAVBL == 0.0) // if no scaling factor is given, then scale to mean bond length = 1.5
					targetAVBL = 1.5f;

				if (targetAVBL != 0.0f && mMol.getAllBonds() != 0) {
					float avbl = 0.0f;
					for (int bond=0; bond<mMol.getAllBonds(); bond++) {
						float dx = mMol.getAtomX(mMol.getBondAtom(0, bond)) - mMol.getAtomX(mMol.getBondAtom(1, bond));
						float dy = mMol.getAtomY(mMol.getBondAtom(0, bond)) - mMol.getAtomY(mMol.getBondAtom(1, bond));
						float dz = coordsAre3D ? mMol.getAtomZ(mMol.getBondAtom(0, bond)) - mMol.getAtomZ(mMol.getBondAtom(1, bond)) : 0.0f;
						avbl += Math.sqrt(dx*dx + dy*dy + dz*dz);
						}
					avbl /= mMol.getAllBonds();
					float f = targetAVBL / avbl;
					for (int atom=0; atom<mMol.getAllAtoms(); atom++) {
						mMol.setAtomX(atom, mMol.getAtomX(atom) * f + xOffset);
						mMol.setAtomY(atom, mMol.getAtomY(atom) * f + yOffset);
						if (coordsAre3D)
							mMol.setAtomZ(atom, mMol.getAtomZ(atom) * f + zOffset);
						}
					}
				}
			}

		new AromaticityResolver(mMol, isAromaticBond).locateDelocalizedDoubleBonds();

		if (aromaticSPBond != null)
			for (int bond:aromaticSPBond)
				mMol.setBondType(bond, mMol.getBondType(bond) == Molecule.cBondTypeDouble ?
						Molecule.cBondTypeTriple : Molecule.cBondTypeDouble);

		boolean coords2DAvailable = (coordinates != null && !coordsAre3D);

		// If we have or create 2D-coordinates, then we need to set all double bonds to a cross bond, which
		// - have distinguishable substituents on both ends, i.e. is a stereo double bond
		// - are not in a small ring
		// Here we don't know, whether a double bond without E/Z parity is a stereo bond with unknown
		// configuration or not a stereo bond. Therefore we need to set a flag, that causes the Canonizer
		// during the next stereo recognition with atom coordinates to assign an unknown configuration rather
		// than E or Z based on created or given coordinates.
		// In a next step these double bonds are converted into cross bonds by
		if (coords2DAvailable || mEnsure2DCoordinates) {
			mMol.ensureHelperArrays(Molecule.cHelperRings);
			for (int bond=0; bond<mMol.getBonds(); bond++)
				if (mMol.getBondOrder(bond) == 2
				 && !mMol.isSmallRingBond(bond)
				 && mMol.getBondParity(bond) == Molecule.cBondParityNone)
					mMol.setBondParityUnknownOrNone(bond);
			}

		if (!coords2DAvailable && mEnsure2DCoordinates) {
			CoordinateInventor inventor = new CoordinateInventor();
			inventor.setRandomSeed(0x1234567890L);  // create reproducible coordinates
			inventor.invent(mMol);
			coords2DAvailable = true;
			}

		if (coords2DAvailable) {
			mMol.setStereoBondsFromParity();
			mMol.setUnknownParitiesToExplicitlyUnknown();
			}
		else if (!coordsAre3D) {
			mMol.setParitiesValid(0);
			}
		}

	public void parseMapping(byte[] mapping) {
		if (mapping == null || mapping.length == 0)
			return;

		decodeBitsStart(mapping, 0);
		int nbits = decodeBits(4);
		boolean autoMappingFound = (decodeBits(1) == 1);
		boolean manualMappingFound = (decodeBits(1) == 1);
		for (int atom=0; atom<mMol.getAtoms(); atom++) {
			int mapNo = decodeBits(nbits);
			boolean autoMapped = autoMappingFound;
			if (autoMappingFound && manualMappingFound)
				autoMapped = (decodeBits(1) == 1);
			mMol.setAtomMapNo(atom, mapNo, autoMapped);
			}
		}

	public boolean coordinatesAre3D(String idcode, String coordinates) {
		return (coordinates == null) ? false : coordinatesAre3D(idcode.getBytes(), coordinates.getBytes());
		}

	public boolean coordinatesAre3D(byte[] idcode, byte[] coordinates) {
		if (coordinates == null || coordinates.length == 0)
			return false;

		if (coordinates[0] == '!') {	// current version starts with '!' (ASC 33), further versions may start with ASC 34 to 38
			decodeBitsStart(coordinates, 1);
			return (decodeBits(1) == 1);
			}
		else {	// old format uses ACSII 39 and higher
			int allAtoms = getAtomCount(idcode, 0);
			return (allAtoms != 0
				 && coordinates.length >= 3*allAtoms-3
				 && coordinates[2*allAtoms-2] != '\'');
			}
		}

	public boolean coordinatesAreAbsolute(String coordinates) {
		return (coordinates == null) ? false : coordinatesAreAbsolute(coordinates.getBytes());
		}

	public boolean coordinatesAreAbsolute(byte[] coordinates) {
		if (coordinates == null || coordinates.length == 0)
			return false;

		if (coordinates[0] >= '\'') {	// old format uses ACSII 39 and higher
			for (int i=0; i<coordinates.length; i++)
				if (coordinates[i] == '\'' || coordinates[i] == '&')
					return true;
			}
		else if (coordinates[0] == '!') {	// current version starts with '!' (ASC 33), further versions may start with ASC 34 to 38
			decodeBitsStart(coordinates, 1);
			decodeBits(1);	// skip 3D information
			return (decodeBits(1) == 1);
			}

		return false;
		}

	public int getIDCodeVersion(String idcode) {
		if (idcode == null || idcode.length() == 0)
			return -1;

		return getIDCodeVersion(idcode.getBytes());
		}

	public int getIDCodeVersion(byte[] idcode) {
		int version = Canonizer.cIDCodeVersion2;

		decodeBitsStart(idcode, 0);
		int abits = decodeBits(4);
		if (abits > 8)	// abits is the version number
			version = abits;

		return version;
		}

	public int getAtomCount(String idcode) {
		if (idcode == null || idcode.length() == 0)
			return 0;

		return getAtomCount(idcode.getBytes(), 0);
		}

	public int getAtomCount(byte[] idcode, int offset) {
		if (idcode == null || idcode.length <= offset)
			return 0;

		decodeBitsStart(idcode, offset);
		int abits = decodeBits(4);
		int bbits = decodeBits(4);

		if (abits > 8)	// abits is the version number
			abits = bbits;

		return decodeBits(abits);
		}

	private void decodeBitsStart(byte[] bytes, int offset) {
		mIDCodeBitsAvail = 6;
		mIDCodeBufferIndex = offset;
		mDecodingBytes = bytes;
		mIDCodeTempData = (bytes[mIDCodeBufferIndex] - 64) << 11;
		}

	private int decodeBits(int bits) {
		int allBits = bits;

		int data = 0;
		while (bits != 0) {
			if (mIDCodeBitsAvail == 0) {
				mIDCodeTempData = (mDecodingBytes[++mIDCodeBufferIndex] - 64) << 11;
				mIDCodeBitsAvail = 6;
				}
			data |= ((0x00010000 & mIDCodeTempData) >> (16 - allBits + bits));
			mIDCodeTempData <<= 1;
			bits--;
			mIDCodeBitsAvail--;
			}
		return data;
		}

	private float decodeAVBL(int value, int binCount) {
		return (float)Math.pow(10, Math.log10(200/0.1) * value / (binCount - 1) - 1f);
		}

	private float decodeShift(int value, int binCount) {
		int halfBinCount = binCount / 2;
		boolean isNegative = (value >= halfBinCount);
		if (isNegative)
			value -= halfBinCount;
		float steepness = (float)binCount/100f;
		float floatValue = steepness * value / ((float)halfBinCount - 1 - value);
		return isNegative ? -floatValue : floatValue;
		}

	public void printContent(byte[] idcode, byte[] coordinates) {
		int version = Canonizer.cIDCodeVersion2;

		System.out.println("IDCode: "+new String(idcode));
		if (idcode==null || idcode.length==0)
			return;

		decodeBitsStart(idcode, 0);
		int abits = decodeBits(4);
		int bbits = decodeBits(4);

		if (abits > 8) {	// abits is the version number
			version = abits;
			abits = bbits;
			}

		System.out.println("version:"+version);

		int allAtoms = decodeBits(abits);
		if (allAtoms == 0)
			return;

		int allBonds = decodeBits(bbits);
		int nitrogens = decodeBits(abits);
		int oxygens = decodeBits(abits);
		int otherAtoms = decodeBits(abits);
		int chargedAtoms = decodeBits(abits);

		System.out.println("allAtoms:"+allAtoms+" allBonds:"+allBonds);
		if (nitrogens != 0) {
			System.out.print("nitrogens:");
			for (int i=0; i<nitrogens; i++)
				System.out.print(" "+decodeBits(abits));
			System.out.println();
			}
		if (oxygens != 0) {
			System.out.print("oxygens:");
			for (int i=0; i<oxygens; i++)
				System.out.print(" "+decodeBits(abits));
			System.out.println();
			}
		if (otherAtoms != 0) {
			System.out.print("otherAtoms:");
			for (int i=0; i<otherAtoms; i++)
				System.out.print(" "+decodeBits(abits)+":"+decodeBits(8));
			System.out.println();
			}
		if (chargedAtoms != 0) {
			System.out.print("chargedAtoms:");
			for (int i=0; i<chargedAtoms; i++)
				System.out.print(" "+decodeBits(abits)+":"+(decodeBits(4)-8));
			System.out.println();
			}

		int closureBonds = 1 + allBonds - allAtoms;
		int dbits = decodeBits(4);
		int base = 0;

		int[][] bondAtom = new int[2][allBonds];
		int bondCount = 0;
		for (int i=1; i<allAtoms; i++) {
			int dif = decodeBits(dbits);
			if (dif == 0) {
				closureBonds++;
				continue;
				}
			base += dif - 1;
			bondAtom[0][bondCount] = base;
			bondAtom[1][bondCount++] = i;
			}

		for (int i=0; i<closureBonds; i++) {
			bondAtom[0][bondCount] = decodeBits(abits);
			bondAtom[1][bondCount++] = decodeBits(abits);
			}

		int[] bondOrder = new int[allBonds];
		System.out.print("bonds:");
		for (int bond=0; bond<allBonds; bond++) {
			System.out.print(" "+bondAtom[0][bond]);
			bondOrder[bond] = decodeBits(2);
			System.out.print(bondOrder[bond]==0?".":bondOrder[bond]==1?"-":bondOrder[bond]==2?"=":"#");
			System.out.print(""+bondAtom[1][bond]);
			}
		System.out.println();

		int THCount = decodeBits(abits);
		if (THCount != 0) {
			System.out.print("parities:");
			for (int i=0; i<THCount; i++) {
				int atom = decodeBits(abits);
				if (version == Canonizer.cIDCodeVersion2) {
					int parity = decodeBits(2);
					if (parity == 3) {
						// this was the old discontinued Molecule.cAtomParityMix
						// version2 idcodes had never more than one center with parityMix
						System.out.print(" "+atom+":1&0");
						}
					else {
						System.out.print(" "+atom+":"+parity);
						}
					}
				else {
					int parity = decodeBits(3);
					switch (parity) {
					case Canonizer.cParity1And:
						System.out.print(" "+atom+":1&"+decodeBits(3));
						break;
					case Canonizer.cParity2And:
						System.out.print(" "+atom+":2&"+decodeBits(3));
						break;
					case Canonizer.cParity1Or:
						System.out.print(" "+atom+":1|"+decodeBits(3));
						break;
					case Canonizer.cParity2Or:
						System.out.print(" "+atom+":2|"+decodeBits(3));
						break;
					default:
						System.out.print(" "+atom+":"+parity);
						}
					}
				}
			System.out.println();
			}

		if (version == Canonizer.cIDCodeVersion2)
			if ((decodeBits(1) == 0))   // translate chiral flag
				System.out.print("isRacemate");

		int EZCount = decodeBits(bbits);
		if (EZCount != 0) {
			System.out.print("EZ:");
			for (int i=0; i<EZCount; i++) {
				int bond = decodeBits(bbits);
				if (bondOrder[bond] == 1) {	// BINAP type of axial chirality
					int parity = decodeBits(3);
					switch (parity) {
					case Canonizer.cParity1And:
						System.out.print(" "+bond+":1&"+decodeBits(3));
						break;
					case Canonizer.cParity2And:
						System.out.print(" "+bond+":2&"+decodeBits(3));
						break;
					case Canonizer.cParity1Or:
						System.out.print(" "+bond+":1|"+decodeBits(3));
						break;
					case Canonizer.cParity2Or:
						System.out.print(" "+bond+":2|"+decodeBits(3));
						break;
					default:
						System.out.print(" "+bond+":"+parity);
						}
					}
				else
					System.out.print(" "+bond+":"+decodeBits(2));
				}
			System.out.println();
			}
		
		if (decodeBits(1) == 1)
			System.out.println("isFragment = true");

		int offset = 0;
		while (decodeBits(1) == 1) {
			int dataType = offset + decodeBits(4);
			switch (dataType) {
			case 0: //  datatype 'AtomQFNoMoreNeighbours'
				int no = decodeBits(abits);
				System.out.print("noMoreNeighbours:");
				for (int i=0; i<no; i++)
					System.out.print(" "+decodeBits(abits));
				System.out.println();
				break;
			case 1: //  datatype 'isotop'
				no = decodeBits(abits);
				System.out.print("mass:");
				for (int i=0; i<no; i++)
					System.out.print(" "+decodeBits(abits)+":"+decodeBits(8));
				System.out.println();
				break;
			case 2: //  datatype 'bond defined to be delocalized'
				no = decodeBits(bbits);
				System.out.print("delocalizedBonds:");
				for (int i=0; i<no; i++)
					System.out.print(" "+decodeBits(bbits));
				System.out.println();
				break;
			case 3: //  datatype 'AtomQFMoreNeighbours'
				no = decodeBits(abits);
				System.out.print("moreNeighbours:");
				for (int i=0; i<no; i++)
					System.out.print(" "+decodeBits(abits));
				System.out.println();
				break;
			case 4: //  datatype 'AtomQFRingState'
				no = decodeBits(abits);
				System.out.print("atomRingState:");
				for (int i=0; i<no; i++)
					System.out.print(" "+decodeBits(abits)+":"+decodeBits(Molecule.cAtomQFRingStateBits));
				System.out.println();
				break;
			case 5: //  datatype 'AtomQFAromState'
				no = decodeBits(abits);
				System.out.print("atomAromState:");
				for (int i=0; i<no; i++)
					System.out.print(" "+decodeBits(abits)+":"+decodeBits(Molecule.cAtomQFAromStateBits));
				System.out.println();
				break;
			case 6: //  datatype 'AtomQFAny'
				no = decodeBits(abits);
				System.out.print("atomAny:");
				for (int i=0; i<no; i++)
					System.out.print(" "+decodeBits(abits));
				System.out.println();
				break;
			case 7: //  datatype 'AtomQFHydrogen'
				no = decodeBits(abits);
				System.out.print("atomHydrogen:");
				for (int i=0; i<no; i++)
					System.out.print(" "+decodeBits(abits)+":"+decodeBits(Molecule.cAtomQFHydrogenBits));
				System.out.println();
				break;
			case 8: //  datatype 'AtomList'
				no = decodeBits(abits);
				System.out.print("atomList:");
				for (int i=0; i<no; i++) {
					int atom = decodeBits(abits);
					int atoms = decodeBits(4);
					System.out.print(" "+atom);
					for (int j=0; j<atoms; j++) {
						System.out.print(j==0?":":",");
						System.out.print(""+decodeBits(8)+",");
						}
					}
				System.out.println();
				break;
			case 9: //  datatype 'BondQFRingState'
				no = decodeBits(bbits);
				System.out.print("bondRingState:");
				for (int i=0; i<no; i++)
					System.out.print(" "+decodeBits(bbits)+":"+decodeBits(Molecule.cBondQFRingStateBits));
				System.out.println();
				break;
			case 10://  datatype 'BondQFBondTypes'
				no = decodeBits(bbits);
				System.out.print("bondTypes:");
				for (int i=0; i<no; i++)
					System.out.print(" "+decodeBits(bbits)+":"+decodeBits(Molecule.cBondQFBondTypesBits));
				System.out.println();
				break;
			case 11:	//  datatype 'AtomQFMatchStereo'
				no = decodeBits(abits);
				System.out.print("atomMatchStereo:");
				for (int i=0; i<no; i++)
					System.out.print(" "+decodeBits(abits));
				System.out.println();
				break;
			case 12:	//  datatype 'bond defined to be a bridge from n1 to n2 atoms'
				no = decodeBits(bbits);
				for (int i=0; i<no; i++) {
					System.out.print("bridgeBond:"+decodeBits(bbits));
					int min = decodeBits(Molecule.cBondQFBridgeMinBits);
					int max = min + decodeBits(Molecule.cBondQFBridgeSpanBits);
					System.out.println("("+min+"-"+max+")");
					}
				break;
			case 13: //  datatype 'AtomQFPiElectrons'
				no = decodeBits(abits);
				System.out.print("atomPiElectrons:");
				for (int i=0; i<no; i++)
					System.out.print(" "+decodeBits(abits)+":"+decodeBits(Molecule.cAtomQFPiElectronBits));
				System.out.println();
				break;
			case 14: //  datatype 'AtomQFNeighbours'
				no = decodeBits(abits);
				System.out.print("AtomQFNeighbours:");
				for (int i=0; i<no; i++)
					System.out.print(" "+decodeBits(abits)+":"+decodeBits(Molecule.cAtomQFNeighbourBits));
				System.out.println();
				break;
			case 15: //  datatype 'start second feature set'
				offset = 16;
				System.out.println("<start second feature set>");
				break;
			case 16: //  datatype 'AtomQFRingSize'
				no = decodeBits(abits);
				System.out.print("AtomQFRingSize:");
				for (int i=0; i<no; i++)
					System.out.print(" "+decodeBits(abits)+":"+decodeBits(Molecule.cAtomQFRingSizeBits));
				System.out.println();
				break;
			case 17: //  datatype 'AtomAbnormalValence'
				no = decodeBits(abits);
				System.out.print("AtomAbnormalValence:");
				for (int i=0; i<no; i++)
					System.out.print(" "+decodeBits(abits)+":"+decodeBits(4));
				System.out.println();
				break;
			case 18: //  datatype 'AtomCustomLabel'
				no = decodeBits(abits);
				System.out.print("AtomCustomLabel:");
				int lbits = decodeBits(4);
				for (int i=0; i<no; i++) {
					int atom = decodeBits(abits);
					int count = decodeBits(lbits);
					byte[] label = new byte[count];
					for (int j=0; j<count; j++)
						label[j] = (byte)decodeBits(7);
					System.out.print(" "+atom+":"+new String(label));
					}
				System.out.println();
				break;
			case 19: //  datatype 'AtomQFCharge'
				no = decodeBits(abits);
				System.out.print("AtomQFCharge:");
				for (int i=0; i<no; i++)
					System.out.print(" "+decodeBits(abits)+":"+decodeBits(Molecule.cAtomQFChargeBits));
				System.out.println();
				break;
			case 20: //  datatype 'BondQFRingSize'
				no = decodeBits(bbits);
				System.out.print("BondQFRingSize:");
				for (int i=0; i<no; i++)
					System.out.print(" "+decodeBits(bbits)+":"+decodeBits(Molecule.cBondQFRingSizeBits));
				System.out.println();
				break;
			case 21: //  datatype 'AtomRadicalState'
				no = decodeBits(abits);
				System.out.print("AtomRadicalState:");
				for (int i=0; i<no; i++)
					System.out.print(" "+decodeBits(abits)+":"+decodeBits(2));
				System.out.println();
				break;
			case 22:	//	datatype 'flat nitrogen'
				no = decodeBits(abits);
				System.out.print("AtomQFFlatNitrogen:");
				for (int i=0; i<no; i++)
					System.out.print(" "+decodeBits(abits)+":true");
				System.out.println();
				break;
			case 23:	//	datatype 'cBondQFMatchStereo'
				no = decodeBits(bbits);
				System.out.print("cBondQFMatchStereo:");
				for (int i=0; i<no; i++)
					System.out.print(" "+decodeBits(abits)+":true");
				System.out.println();
				break;
			case 24:	//	datatype 'cBondQFAromatic'
				no = decodeBits(bbits);
				System.out.print("BondQFAromState:");
				for (int i=0; i<no; i++)
					System.out.print(" "+decodeBits(bbits)+":"+decodeBits(Molecule.cBondQFAromStateBits));
				System.out.println();
				break;
			case 25:	//	datatype 'atom selection'
				System.out.print("AtomSelection:");
				for (int i=0; i<allAtoms; i++)
					if (decodeBits(1) == 1)
						System.out.print(" "+i);
				System.out.println();
				break;
			case 26:	//	datatype 'delocalized high order bond'
				System.out.print("DelocalizedHigherOrderBonds:");
				no = decodeBits(bbits);
				for (int i=0; i<no; i++)
					System.out.print(" "+decodeBits(bbits));
				break;
				}
			}

		if (coordinates != null) {
			if (coordinates[0] == '!') {	// new coordinate format
				decodeBitsStart(coordinates, 1);
				boolean coordsAre3D = (decodeBits(1) == 1);
				System.out.print((decodeBits(1) == 1) ? "absolute coords:" : "relative coords:");
				int resolutionBits = 2 * decodeBits(4);
				int binCount = (1 << resolutionBits);
	
				float factor = 0.0f;
				float[][] coords = new float[coordsAre3D?3:2][allAtoms];
				int from = 0;
				int bond = 0;
				for (int atom=1; atom<allAtoms; atom++) {
					if (bond<allBonds && bondAtom[1][bond] == atom) {
						from = bondAtom[0][bond++];
						factor = 1.0f;
						}
					else {
						from = 0;
						factor = 8.0f;
						}
					coords[0][atom] = coords[0][from] + factor * (decodeBits(resolutionBits) - binCount/2);
					coords[1][atom] = coords[1][from] + factor * (decodeBits(resolutionBits) - binCount/2);
					if (coordsAre3D)
						coords[2][atom] = coords[2][from] + factor * (decodeBits(resolutionBits) - binCount/2);
					}
	
				// with new format 2D and 3D coordinates are scaled to average bond lengths of 1.5 Angstrom
				float avbl = 0f;
				for (bond=0; bond<allBonds; bond++) {
					float dx = coords[0][bondAtom[0][bond]] - coords[0][bondAtom[1][bond]];
					float dy = coords[1][bondAtom[0][bond]] - coords[1][bondAtom[1][bond]];
					float dz = coordsAre3D ? coords[2][bondAtom[0][bond]] - coords[2][bondAtom[1][bond]] : 0f;
					avbl += Math.sqrt(dx*dx + dy*dy + dz*dz);
					}
				avbl /= allBonds;
				float targetAVBL = 1.5f;
				factor = targetAVBL / avbl;
				for (int atom=0; atom<allAtoms; atom++) {
					coords[0][atom] = coords[0][atom] * factor;
					coords[1][atom] = coords[1][atom] * factor;
					if (coordsAre3D)
						coords[2][atom] = coords[2][atom] * factor;
					}
	
				System.out.print("coords:");
				for (int atom=0; atom<allAtoms; atom++) {
					System.out.print(" "+((int)(1000f*coords[0][atom]))/1000f+","+((int)(1000f*coords[1][atom]))/1000f);
					if (coordsAre3D)
						System.out.print(","+((int)(1000f*coords[2][atom]))/1000f);
					}
				System.out.println();
				}
			}
		System.out.println();
		}
	}