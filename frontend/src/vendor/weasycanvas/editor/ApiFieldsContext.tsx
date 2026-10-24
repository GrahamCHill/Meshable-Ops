"use client";
import { createContext, useContext, useMemo, type ReactNode } from "react";
import type { PdfEditorApiField, PdfEditorApiValues } from "./apiFields";

const emptyFields: PdfEditorApiField[] = [], emptyValues: PdfEditorApiValues = {};
const Context = createContext({ fields: emptyFields, values: emptyValues });
export function ApiFieldsProvider({ fields = emptyFields, values = emptyValues, children }: { fields?: PdfEditorApiField[]; values?: PdfEditorApiValues; children: ReactNode }) {
  const context = useMemo(() => ({ fields, values }), [fields, values]);
  return <Context.Provider value={context}>{children}</Context.Provider>;
}
export const useApiFields = () => useContext(Context);
