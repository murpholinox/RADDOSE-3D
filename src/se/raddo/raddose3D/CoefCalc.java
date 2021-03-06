package se.raddo.raddose3D;

import java.util.Set;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

public abstract class CoefCalc {
  /**
   * Calculate cross-sections for the new photon energy.
   * 
   * @param photonEnergy
   *    The energy of each photon
   */
  public abstract void updateCoefficients(double photonEnergy);

  /**
   * Calculate cross-sections for the new Beam.
   * 
   * @param b
   *    Beam object
   */
  public abstract void updateCoefficients(Beam b);
  
  /**
   * Returns the current absorption coefficient. (Photoelectric)
   * 
   * @return
   *         absorption coefficient in units m^-1 (tbc)
   */
  public abstract double getAbsorptionCoefficient();

  /**
   * Returns the current attenuation coefficient (photoelectric effect +
   * inelastic/compton scattering + elastic/rayleigh scattering).
   * 
   * @return
   *         attenuation coefficient in units m^-1 (tbc)
   */
  public abstract double getAttenuationCoefficient();

  /**
   * Returns the current elastic X-ray (rayleigh, coherent) scattering
   * coefficient.
   * 
   * @return
   *         elastic scattering coefficient in units m^-1 (tbc)
   */
  public abstract double getElasticCoefficient();
  public abstract double getElasticCoefficientMacro();

  /**
   * Returns the current inelastic X-ray (compton, incoherent) scattering
   * coefficient.
   * 
   * @return
   *         inelastic scattering coefficient in units m^-1 (tbc)
   */
  public abstract double getInelasticCoefficient();
  
  /**
   * Returns the density of the crystal.
   * 
   * @return
   *         crystal density in g/ml.
   */ 
  
  public abstract double getDensity();
  
  /**
   * Calculates factors used to calculate fluorescence energy for the crystal
   * 
   * @param beam
   *          Beam object
   * @return
   *         fluorescence factors.
   */
  public abstract double[][] getFluorescentEscapeFactors(Beam beam);
  
  /**
   * Calculate cryo-cross-sections for the new Beam.
   * 
   * @param photonEnergy
   *    The energy of each photon
   */
  public abstract void updateCryoCoefficients(final double photonEnergy);

  /**
   * Calculate cryo-cross-sections for the new Beam.
   * 
   * @param b
   *    Beam object
   */
  public abstract void updateCryoCoefficients(Beam b);  
  
  
  /**
   * Returns the current cryo absorption coefficient. (Photoelectric and Compton)
   * 
   * @return
   *         absorption coefficient in units m^-1 (tbc)
   */
  public abstract double getCryoAbsorptionCoefficient();
  
  /**
   * Returns the density of the cryo-solution.
   * 
   * @return
   *         cryo-solution density in g/ml.
   */ 
  
  public abstract double getCryoDensity();
  
  /**
   * @return
   *        if cryo solution has been entered or not
   */
  public abstract boolean isCryo();
  
  /**
   * Calculates factors used to calculate fluorescence energy for cryo solution
   * 
   * @param beam
   *          Beam object
   * @return
   *         fluorescence factors.
   */
  public abstract double[][] getCryoFluorescentEscapeFactors(Beam beam);
  
  /**
   * Returns the elements present in the crystal or the surrounding
   * @param cryo
   */
  public abstract Set<Element> getPresentElements(boolean cryo);
  
  public abstract double getElectronElastic(double avgEnergy);
  
  public abstract double getElectronInelastic(double avgEnergy, double exposedVolume);
  
  public abstract double getStoppingPower(double avgEnergy, boolean surrounding);
  
  public abstract double getEta();
  
  public abstract Map<String, Double> getFractionElementEM();
  
  public abstract double getElectronElasticMFPL(double electronEnergy, boolean surrounding );
  
  public abstract double getRutherfordScreening(double electronEnergy);
  
  public abstract double getFSELambda(double FSExSection, boolean surrounding);
  
  public abstract double betheIonisationxSection(double electronEnergy, boolean surrounding);
  
  // public abstract Map<Element, Double> getInnerShellProbs();
  
  public abstract Map<Element, double[]> getAllShellProbs(boolean surrounding);
 
  public abstract Map<ElementEM, Double> getElasticProbs(boolean surrounding);
  
  public abstract double getEMFlAbsCoef(double flEnergy);
  
  public abstract void calculateSterheimerFactor();
  
  public abstract double getZav();
  
  public abstract void populateCrossSectionCoefficients();
  
  public abstract double getPlasmaMFPL(double electronEnergy, boolean surrounding);
  
  public abstract double getPlasmaFrequency(boolean surrounding);
  
  public abstract double getElectronInelasticMFPL(double electronEnergy, boolean surrounding);
  
  public abstract long getNumberSimulatedElectrons();
  
  public abstract double getSolventFraction();
  
  public abstract Map<Element, Double> getPhotoElectricProbsElement(double beamEnergy);
  public abstract Map<Element, Double> getComptonProbsElement(double beamEnergy);
  
  public abstract double getElementAbsorptionCoef(double beamEnergy, Element e);
  
  public abstract double getCryoInelasticCoefficient();
  
  public abstract Map<Element, Double> getPhotoElectricProbsElementSurrounding(double beamEnergy);
  
  public abstract double getTotalAtomsInCrystal(double crystalVolume);
  
  public abstract double getTotalAtomsInCrystalElement(double crystalVolume, Element elem);
  
  public abstract void getDifferentialInlasticxSection(double electronEnergy, boolean surrounding);
  
  public abstract double getGOSInel(boolean surrounding, double E);
  
  public abstract Map<Element, double[]> getGOSShellProbs(boolean surrounding, double totLambda);
  
  public abstract Map<Element, Double> getGOSOuterShellProbs(boolean surrounding, double totLambda);
  
  public abstract Map<Element, double[][]> getGOSVariable(boolean surrounding);
  
  public abstract double[] getPlasmonVariable (boolean surrounding);
  
  public abstract double returnAdjustment();
  
  public abstract double getWkMolecule(double a, Element e, int shellIndex, boolean surrounding);
  
  public abstract double getWcbAll(boolean surrounding);
  
  public abstract double getRecoilEnergyDistant(double EkeV, double WakeV, double Qak);
  
  public abstract double getAvgInelasticEnergy(double E);
  
  public abstract double getGOSOuterLambda(boolean surrounding);
  
  public abstract double getGOSInnerLambda(boolean surrounding);
  
  public abstract int[] getNumValenceElectronsSubshells(Element e);

  public abstract double getPlasmaEnergy(boolean surrounding);
  
  public abstract Double getProteinAtoms(final ElementEM element); 
  public abstract Double getSolventAtoms(final ElementEM element); 
  
  public abstract Map<String, Double> calculateCoefficientsSilicon(double energy);
  
}

