/**
 * Wewnętrzne API dla workerów integracji (D7): worker nie łączy się z bazą, tylko pobiera dane doręczeń i przekazań
 * oraz raportuje statusy. Każde żądanie ma token dostępu ID-users (client credentials) weryfikowany po podpisie RS256
 * i wymaga roli {@code eskarga-worker}. Dane osobowe nie opuszczają sieci wewnętrznej TK.
 */
package pl.ejtechnology.tk.eSkarga.api;
