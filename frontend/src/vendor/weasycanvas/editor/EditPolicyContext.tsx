"use client";

import { createContext, useContext } from "react";
import type { PdfEditorEditPermissions } from "./types";

const allow = () => true;
const Context = createContext<PdfEditorEditPermissions>({ canEditField: allow, canEditSection: allow, canChangeStructure: allow });
export const EditPolicyProvider = Context.Provider;
export const useEditPermissions = () => useContext(Context);
