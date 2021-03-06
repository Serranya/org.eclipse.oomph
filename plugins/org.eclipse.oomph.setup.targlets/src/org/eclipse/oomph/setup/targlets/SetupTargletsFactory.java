/*
 * Copyright (c) 2014, 2015 Eike Stepper (Loehne, Germany) and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 *    Eike Stepper - initial API and implementation
 */
package org.eclipse.oomph.setup.targlets;

import org.eclipse.emf.ecore.EFactory;

import org.eclipse.equinox.p2.metadata.Version;

/**
 * <!-- begin-user-doc -->
 * The <b>Factory</b> for the model.
 * It provides a create method for each non-abstract class of the model.
 * <!-- end-user-doc -->
 * @see org.eclipse.oomph.setup.targlets.SetupTargletsPackage
 * @generated
 */
public interface SetupTargletsFactory extends EFactory
{
  /**
   * The singleton instance of the factory.
   * <!-- begin-user-doc -->
   * <!-- end-user-doc -->
   * @generated
   */
  SetupTargletsFactory eINSTANCE = org.eclipse.oomph.setup.targlets.impl.SetupTargletsFactoryImpl.init();

  /**
   * Returns a new object of class '<em>Targlet Task</em>'.
   * <!-- begin-user-doc -->
   * <!-- end-user-doc -->
   * @return a new object of class '<em>Targlet Task</em>'.
   * @generated
   */
  TargletTask createTargletTask();

  /**
   * Returns a new object of class '<em>Implicit Dependency</em>'.
   * <!-- begin-user-doc -->
   * <!-- end-user-doc -->
   * @return a new object of class '<em>Implicit Dependency</em>'.
   * @generated
   */
  ImplicitDependency createImplicitDependency();

  ImplicitDependency createImplicitDependency(String id, Version version);

  ImplicitDependency createImplicitDependency(String id, String version);

  /**
   * Returns the package supported by this factory.
   * <!-- begin-user-doc -->
   * <!-- end-user-doc -->
   * @return the package supported by this factory.
   * @generated
   */
  SetupTargletsPackage getSetupTargletsPackage();

} // SetupTargletsFactory
