/*
 * Copyright 2006-2020 The MZmine Development Team
 * 
 * This file is part of MZmine.
 * 
 * MZmine is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the
 * License, or (at your option) any later version.
 * 
 * MZmine is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with MZmine; if not,
 * write to the Free Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301
 * USA
 */

package io.github.mzmine.modules.dataprocessing.id_lipididentification.lipidutils;

import java.util.Map;

import javax.annotation.Nonnull;

import io.github.mzmine.datamodel.impl.SimplePeakIdentity;
import io.github.mzmine.modules.dataprocessing.id_lipididentification.lipids.LipidClassType;
import io.github.mzmine.modules.dataprocessing.id_lipididentification.lipids.LipidClasses;
import io.github.mzmine.modules.dataprocessing.id_lipididentification.lipids.customlipidclass.CustomLipidClass;
import io.github.mzmine.util.FormulaUtils;

/**
 * lipid identity to annotate features as lipids in feature list
 * 
 * @author Ansgar Korf (ansgar.korf@uni-muenster.de)
 */
public class LipidIdentity extends SimplePeakIdentity {

	private static final LipidFactory LIPID_FACTORY = new LipidFactory();

	private double exactMass;
	private String sumFormula;
	private String name;
	private LipidClasses lipidClass;
	private CustomLipidClass customLipidClass;
	private LipidClassType lipidClassType;

	public LipidIdentity(final LipidClasses lipidClass, final int chainLength, final int chainDoubleBonds,
			LipidChainType[] chainTypes, LipidClassType lipidClassType) {

		this(LIPID_FACTORY.buildLipid(lipidClass, chainLength, chainDoubleBonds, chainTypes).getName(),
				LIPID_FACTORY.buildLipid(lipidClass, chainLength, chainDoubleBonds, chainTypes).getFormula());
		this.lipidClass = lipidClass;
		this.lipidClassType = lipidClassType;
	}

	public LipidIdentity(final CustomLipidClass lipidClass, final int chainLength, final int chainDoubleBonds,
			LipidChainType[] chainTypes, LipidClassType lipidClassType) {

		this(LIPID_FACTORY.buildLipid(lipidClass, chainLength, chainDoubleBonds, chainTypes).getName(),
				LIPID_FACTORY.buildLipid(lipidClass, chainLength, chainDoubleBonds, chainTypes).getFormula());
		this.customLipidClass = lipidClass;
		this.lipidClassType = lipidClassType;
	}

	public LipidIdentity(final String name, final String formula) {
		super(name);
		this.name = name;

		// Parse formula
		Map<String, Integer> parsedFormula = FormulaUtils.parseFormula(formula);

		// Rearrange formula
		sumFormula = FormulaUtils.formatFormula(parsedFormula);
		exactMass = FormulaUtils.calculateExactMass(sumFormula);
		setPropertyValue(PROPERTY_NAME, name);
		setPropertyValue(PROPERTY_FORMULA, sumFormula);
		setPropertyValue(PROPERTY_METHOD, "Lipid identification");
	}

	public double getMass() {
		return exactMass;
	}

	public String getFormula() {
		return sumFormula;
	}

	public LipidClasses getLipidClass() {
		return lipidClass;
	}

	public CustomLipidClass getCustomLipidClass() {
		return customLipidClass;
	}

	public LipidClassType getLipidClassType() {
		return lipidClassType;
	}

	@Override
	public @Nonnull Object clone() {
		return new LipidIdentity(getName(), getPropertyValue(PROPERTY_FORMULA));
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((name == null) ? 0 : name.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		LipidIdentity other = (LipidIdentity) obj;
		if (name == null) {
			if (other.name != null)
				return false;
		} else if (!name.equals(other.name))
			return false;
		return true;
	}

	}
