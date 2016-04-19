package simulator;
import simulator.*;
//
import java.io.FileNotFoundException;
import java.io.IOException;
//
import java.util.*;

import static simulator.ProcessControlBlock.State.READY;
import static simulator.ProcessControlBlock.State.TERMINATED;
import static simulator.ProcessControlBlock.State.WAITING;

/**
 * Concrete Kernel type
 * 
 * @author Stephan Jamieson
 * @version 8/3/15
 */
public class FCFSKernel implements Kernel {
    

    //changed to a linkedlist because it's less complex
    private LinkedList<ProcessControlBlock> readyQueue;
    //this is a bad idea, but so is implementing an OS in Java
    private static int pid = 1;

    public FCFSKernel() {
		// Set up the ready queue.
        this.readyQueue = new LinkedList<ProcessControlBlock>();
    }
    
    private ProcessControlBlock dispatch() {
		// Perform context switch, swapping process
        Config.getSimulationClock().logContextSwitch();
        ProcessControlBlock prev_process = Config.getCPU().contextSwitch(Config.getCPU().getCurrentProcess());
		// currently on CPU with one at front of ready queue.
		// If ready queue empty then CPU goes idle ( holds a null value).
        if(readyQueue.poll()==null){
            Config.getCPU().isIdle();
            return null;
        }
		// Returns process removed from CPU.
        return prev_process;
	}
            
    
                
    public int syscall(int number, Object... varargs) {
        int result = 0;
        switch (number) {
             case MAKE_DEVICE:
                {
                    IODevice device = new IODevice((Integer)varargs[0], (String)varargs[1]);
                    Config.addDevice(device);
                }
                break;
             case EXECVE: 
                {
                    ProcessControlBlock pcb = this.loadProgram((String)varargs[0]);
                    if (pcb!=null) {
                        // Loaded successfully.
						// Now add to end of ready queue.
                        readyQueue.add(pcb);
						// If CPU idle then call dispatch.
                        if(Config.getCPU().isIdle()){
                            dispatch();
                        }
                    }
                    else {
                        result = -1;
                    }
                }
                break;
             case IO_REQUEST: 
                {
					// IO request has come from process currently on the CPU.
					// Get PCB from CPU.
                    ProcessControlBlock cpuPCB = Config.getCPU().getCurrentProcess();
					// Find IODevice with given ID: Config.getDevice((Integer)varargs[0]);
                    IODevice ioDevice = Config.getDevice((Integer)varargs[0]);
					// Make IO request on device providing burst time (varages[1]),
					// the PCB of the requesting process, and a reference to this kernel (so // that the IODevice can call interrupt() when the request is completed.
                    ioDevice.requestIO((Integer)varargs[1], cpuPCB, this);
					// Set the PCB state of the requesting process to WAITING.
                    cpuPCB.setState(WAITING);
					// Call dispatch().
                    dispatch();
                }
                break;
             case TERMINATE_PROCESS:
                {
					// Process on the CPU has terminated.
					// Get PCB from CPU.
                    ProcessControlBlock pcb = Config.getCPU().getCurrentProcess();
					// Set status to TERMINATED.
                    pcb.setState(TERMINATED);
                    // Call dispatch().
                    dispatch();
                }
                break;
             default:
                result = -1;
        }
        Config.getSimulationClock().logSystemCall();
        return result;
    }
   
    
    public void interrupt(int interruptType, Object... varargs){
        switch (interruptType) {
            case TIME_OUT:
                throw new IllegalArgumentException("FCFSKernel:interrupt("+interruptType+"...): this kernel does not suppor timeouts.");
            case WAKE_UP:
				// IODevice has finished an IO request for a process.
				// Retrieve the PCB of the process (varargs[1]), set its state
                IODevice ioDevice = Config.getDevice(Integer.parseInt(""+varargs[1]));
                ProcessControlBlock pcb = ioDevice.getProcess();
                // to READY, put it on the end of the ready queue.
                pcb.setState(READY);
                readyQueue.add(pcb);
				// If CPU is idle then dispatch().
                if(Config.getCPU().isIdle()){
                    dispatch();
                }
                break;
            default:
                throw new IllegalArgumentException("FCFSKernel:interrupt("+interruptType+"...): unknown type.");
        }
        Config.getSimulationClock().logInterrupt();
    }
    
    private ProcessControlBlock loadProgram(String filename) {
        try {
            int new_pcb_pid = pid;
            pid++;
            ProcessControlBlock pcb = new ProcessControlBlockImpl(pid);
            return pcb.loadProgram(filename);

        }
        catch (FileNotFoundException fileExp) {
            return null;
        }
        catch (IOException ioExp) {
            return null;
        }
    }
}
