
package sisdist;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 *
 * @author Vitor, Davi
 */
public interface IQuadroAvisos extends Remote {
    
    public void setQuadro(IQuadroAvisos quadro) throws RemoteException;
    public void setAviso(String aviso) throws RemoteException;
    public void notificar(String aviso) throws RemoteException;
    
}
