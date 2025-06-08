package pws;

import pws.PWSState;
import pws.PWSTransition;
import pws.editor.semantics.ExitZone;
import smalgebra.TrueProposition;
import assembly.Action;
import assembly.Assembly;
import machinery.*;
import pws.editor.semantics.Semantics;
import pws.editor.semantics.SemanticsVisitor;
import smalgebra.BasicStateProposition;
import smalgebra.SMProposition;
import java.awt.*;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

public class PWSStateMachine extends StateMachine {
    // Field to hold the Assembly that belongs to this PWSStateMachine.
    private Assembly assembly;

    private static final long serialVersionUID = 1L;

    // Default constructor.
    // public PWSStateMachine() {
    //     super();
    //     // Automatically instantiate a default assembly.
    //     this.setAssembly(new Assembly("PWSEditorAssembly"));
    //     fixPseudoState();
    // }

    // Constructor that accepts a name.
    public PWSStateMachine(String name) {
        super(name);
        // Instantiate a default assembly.
        this.setAssembly(new Assembly("PWSEditorAssembly"));
        fixPseudoState();
    }

    // Constructor that accepts a name and an Assembly.
    // public PWSStateMachine(String name, Assembly assembly) {
    //     super();
    //     this.setName(name);
    //     // Use the provided assembly.
    //     this.setAssembly(assembly);
    //     fixPseudoState();
    // }

    // Constructor that accepts an Assembly.
    // public PWSStateMachine(Assembly assembly) {
    //     super();
    //     // Instantiate with the provided assembly.
    //     this.setAssembly(assembly);
    //     fixPseudoState();
    // }

    // Getter for the assembly.
    public Assembly getAssembly() {
        return assembly;
    }

    // Setter for the assembly.
    public void setAssembly(Assembly assembly) {
        this.assembly = assembly;
    }

    /**
     * Metodo privato per sostituire il pseudostato creato nel costruttore base con un PWSState.
     * Viene rimosso l'oggetto creato di default e sostituito con un'istanza di PWSState.
     */
    private void fixPseudoState() {
        if (!states.isEmpty() && states.get(0).getName().equals("PseudoState")) {
            states.remove(0);
        }
        // Crea il nuovo pseudostato come PWSState
        PWSState pseudo = new PWSState("PseudoState", new Point(20, 20), this.assembly);
        this.pseudoState = pseudo;
        states.add(0, pseudo);
    }

    /**
     * Recalculates and applies the semantics for all states and transitions in this PWSStateMachine.
     *
     * Steps performed:
     * 1) Initialize the pseudostate semantics by calling assembly.calculateInitialStateSemantics().
     * 2) Compute a fixed-point over all other states' semantics via SemanticsVisitor.
     * 3) Assign the newly computed semantics back to each PWSState, skipping the pseudostate to preserve its initial semantics.
     * 4) Update each PWSTransition’s transitionSemantics by computing its pre- and post-conditions.
     */
    public void recalculateSemantics() {
        // Initialize pseudostate semantics
        if (pseudoState instanceof PWSState) {
            PWSState pseudo = (PWSState) pseudoState;
            Semantics init = assembly.calculateInitialStateSemantics();
            pseudo.setStateSemantics(init);
        }

        // Precompute static exit zones based on each state's constraint semantics
        for (StateInterface si : getStates()) {
            if (si instanceof PWSState ps && si != pseudoState) {
                ps.setReactiveSemantics(new HashSet<>(this.findExitZones(ps.getConstraintsSemantics())));
            }
        }

        // Compute fixed-point semantics for all states via SemanticsVisitor
        Map<PWSState, Semantics> semMap = SemanticsVisitor.computeAllStateSemantics(this);

        // ----------------------------------------------------------------------
        // STATE SEMANTICS WRITE-BACK
        // Copy the fixed-point semantics from the visitor’s map into each state’s
        // own field so that all UI annotations (state semantics, exit-zones, etc.)
        // pick up the freshly computed values.
        // ----------------------------------------------------------------------
        // Assign semantics to non-pseudostates
        for (StateInterface s : getStates()) {
            if (s instanceof PWSState && s != pseudoState) {
                PWSState ps = (PWSState) s;
                ps.setStateSemantics(semMap.get(ps));
            }
        }
// ----------------------------------------------------------------------
// TRANSITION SEMANTICS UPDATE
// We still need to recalculate each transition’s pre/post‐semantics
// (for guard badges, action badges and reactive‐zone markers) even
// after the visitor has computed full state semantics.
// ----------------------------------------------------------------------
//        // Update each PWSTransition’s semantics
        for (TransitionInterface t : transitions) {
            if (t instanceof PWSTransition) {
                PWSTransition pt = (PWSTransition) t;
                Semantics ts = computeTransitionSemantics(pt);
                pt.setTransitionSemantics(ts);
            }
        }
        // ----------------------------------------------------------------------
        // LEGACY REACTIVE EXIT-ZONES WRITE-BACK (NO-OP)
        // The reactive exit-zone computation has been moved into
        // SemanticsVisitor.computeAllStateSemantics. This block once updated
        // each state’s exit-zones but now remains commented out for reference.
        // ----------------------------------------------------------------------
//        for (StateInterface si : getStates()) {
//            if (si instanceof PWSState ps && !ps.isPseudoState()) {
//                // Base semantics from stateSemantics
//                Semantics baseSem = ps.getStateSemantics();
//                // Compute exit-zones based on current state semantics
//                HashSet<ExitZone> reactiveZones = computeReactiveSemantics(baseSem);
//                ps.setReactiveSemantics(reactiveZones);
//            }
//        }
    }

    /**
     * LEGACY: Old transition-semantics implementation.
     * This method has been replaced by
     * SemanticsVisitor.computeTransitionContribution(t, base, asm)
     * for fixed-point computation in the visitor.
     * It remains here only to support UI tasks such as
     * displaying per-transition semantics in the editor.
     */
    public Semantics computeTransitionSemantics(PWSTransition t) {
        // Legacy API: use the state’s current semantics as base
        Semantics base = ((PWSState) t.getSource()).getStateSemantics();
        return computeTransitionContribution(t, base);
    }

    /**
     * Compute semantics for a triggerable or initial transition.
     */
//    private Semantics computeTriggerableSemantics(PWSTransition t) {
//        // Get the source state for this transition
//        PWSState src = (PWSState) t.getSource();
//        // Retrieve the full semantics of the source state
//        Semantics stateSem = src.getStateSemantics();
//        // Convert the transition's guard proposition into semantics
//        Semantics guardSem = t.getGuardProposition().toSemantics(assembly);
//        // Compute the intersection of stateSem and the guard semantics
//        Semantics result = stateSem.AND(guardSem);
//        // Apply each associated action event to the result
//        for (Action a : t.getActionList()) {
//            // Transform semantics by this machine event
//            result = result.transformByMachineEvent(a.getMachineId(), a.getEvent(), assembly);
//        }
//        // Return the combined semantics for this triggerable transition
//        return result;
//    }

    /**
     * Compute semantics for a triggerable or initial transition using supplied base semantics.
     * @param t     the transition
     * @param base  the working semantics of the source state
     * @return the transition’s contribution
     */
    public Semantics computeTriggerableSemantics(PWSTransition t, Semantics base) {
        Semantics guardSem = t.getGuardProposition().toSemantics(assembly);
        Semantics result = base.AND(guardSem);
        for (Action a : t.getActionList()) {
            result = result.transformByMachineEvent(a.getMachineId(), a.getEvent(), assembly);
        }
        return result;
    }

    /**
     * LEGACY: Compute semantics for a reactive (autonomous) transition.
     */
    private Semantics computeReactiveTransitionSemantics(PWSTransition t) {
        // Retrieve the source state of the transition
        PWSState src = (PWSState) t.getSource();
        // Get the current full semantics of the source state
        Semantics stateSem = src.getStateSemantics();
        // Cast the transition guard to a BasicStateProposition to use as the reactive trigger
        // Determine the guard proposition for this transition (could be BasicStateProposition or TrueProposition)
        SMProposition guardProp = t.getGuardProposition();
        // Initialize accumulator to ⊥ for reactive contributions
        Semantics result = Semantics.bottom(assembly.getAssemblyId());
        // Iterate over all exit zones of the source state
        for (ExitZone ez : src.getReactiveSemantics()) {
            // Check if this exit zone's target proposition matches the transition guard
            if (guardProp instanceof TrueProposition
                    || ez.getTarget().equals(guardProp)) {
                // Apply the internal machine transition effect on the matching configurations
                Semantics frag = stateSem.transformByMachineTransition(
                        ez.getStateMachineId(),
                        ez.getTransition(),
                        assembly
                );
                // Accumulate this reactive firing into the result
                result = result.OR(frag);
            }
        }
        // Apply any post-actions associated with the transition
        for (Action a : t.getActionList()) {
            result = result.transformByMachineEvent(a.getMachineId(), a.getEvent(), assembly);
        }
        // Return the combined reactive semantics for this transition
        return result;
    }

    /**
     * Compute semantics for a reactive (autonomous) transition using supplied base semantics.
     * @param t     the transition
     * @param base  the working semantics of the source state
     * @return the transition’s contribution
     */
    public Semantics computeReactiveTransitionSemantics(PWSTransition t, Semantics base) {
        Semantics result = Semantics.bottom(assembly.getAssemblyId());
        PWSState src = (PWSState) t.getSource();
        for (ExitZone ez : src.getReactiveSemantics()) {
            if (t.getGuardProposition() instanceof TrueProposition
                    || ez.getTarget().equals(t.getGuardProposition())) {
                Semantics frag = base.transformByMachineTransition(
                        ez.getStateMachineId(), ez.getTransition(), assembly);
                result = result.OR(frag);
            }
        }
        for (Action a : t.getActionList()) {
            result = result.transformByMachineEvent(a.getMachineId(), a.getEvent(), assembly);
        }
        return result;
    }

    /**
     * Compute a transition’s contribution given a working base semantics.
     * @param t     the transition
     * @param base  the working semantics of the source state
     * @return the transition’s contribution
     */
    public Semantics computeTransitionContribution(PWSTransition t, Semantics base) {
        if (t.isTriggerable() || ((PWSState) t.getSource()).isPseudoState()) {
            return computeTriggerableSemantics(t, base);
        } else {
            return computeReactiveTransitionSemantics(t, base);
        }
    }

//    public Semantics computeStateSemantics(PWSState s) {
//        Semantics orSem = Semantics.bottom(assembly.getAssemblyId());
//
//        // Calcola la semantica dalle transizioni entranti
//        List<TransitionInterface> incoming = s.getIncomingTransitions();
//        if (incoming != null) {
//            for (TransitionInterface t : incoming) {
//                if (t instanceof PWSTransition) {
//                    Semantics ts = computeTransitionSemantics((PWSTransition) t);
//                    orSem = (orSem.getConfigurations().isEmpty()) ? ts : orSem.OR(ts);
//                }
//            }
//        }
//
//        // Calcola la semantica autonoma separatamente
//        HashSet<ExitZone> reactiveSem = computeReactiveSemantics(orSem);
//
//        // Potrebbe essere utile assegnare le nuove semantiche al PWSState, per esempio:
//        s.setReactiveSemantics(reactiveSem);
//        // Per quanto riguarda le constraints, potremmo aggiungere una logica simile in futuro.
//
//        return orSem;
//    }

    /**
     * Computes the reactive exit-zones for this state machine given a base semantics.
     *
     * <p>The <b>base semantics</b> is typically associated with a PWSState and denotes its
     * current set of configurations.</p>
     *
     * <p>An <b>exit-zone</b> represents a configuration under which an autonomous
     * (trigger-free) transition in one of the component machines can fire.</p>
     *
     * <p>Concretely, for each autonomous transition:</p>
     * <ol>
     *   <li>The transition’s <i>source state</i> must be included in the provided base semantics, and</li>
     *   <li>The transition’s <i>target state</i> must <em>not</em> yet be part of those semantics.</li>
     * </ol>
     * <p>If both conditions hold, an ExitZone is recorded indicating that firing
     * this transition would add the target state into the semantics.</p>
     *
     * @param baseSemantics the current fixed-point semantics of a source state
     * @return a set of ExitZone objects indicating configurations that immediately trigger
     *         autonomous transitions not yet reflected in the base semantics
     */
    public HashSet<ExitZone> findExitZones(Semantics baseSemantics) {
        HashSet<ExitZone> reactiveSem = new HashSet<>();
        Map<String, StateMachine> stateMachines = assembly.getStateMachines();
        if (stateMachines != null) {
            for (Map.Entry<String, StateMachine> entry : stateMachines.entrySet()) {
                String machineId = entry.getKey();
                StateMachine machine = entry.getValue();
                List<TransitionInterface> allTransitions = machine.getTransitions();
                if (allTransitions != null) {
                    for (TransitionInterface t : allTransitions) {
                        if (t instanceof Transition) {
                            Transition transition = (Transition) t;
                            if (transition.isAutonomous()) {
                                State sourceState = (State) transition.getSource();
                                State targetState = (State) transition.getTarget();
                                BasicStateProposition bs_source = new BasicStateProposition(machineId, sourceState.getName());
                                BasicStateProposition bs_target = null;
                                // una trans. autononome da luogo a una EZ se e solo se:
                                // - la sorgente della bsp ha un'intersezione non nulla con la sem. dello stato
                                // - il target della bsp ha un'intersezione nulla con la sem. dello stato
                                Semantics sourceAndSem = bs_source.toSemantics( assembly ).AND(baseSemantics);
                                if( !sourceAndSem.ISEMPTY()) {
                                    bs_target = new BasicStateProposition(machineId, targetState.getName());
                                    Semantics targetAndSem = bs_target.toSemantics( assembly ).AND(baseSemantics);
                                    if( targetAndSem.ISEMPTY()) {
                                        ExitZone ez = new ExitZone(
                                                machineId,
                                                transition,
                                                bs_source,
                                                bs_target
                                        );
                                        reactiveSem.add(ez);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return reactiveSem;
    }

    @Override
    public PWSStateMachine clone() {
        PWSStateMachine cloned = new PWSStateMachine(this.getName());
        cloned.setAssembly(this.getAssembly());
        // Nota: Per clonare gli stati e le transizioni, occorre implementare la logica di copia,
        // che può essere definita in base alle esigenze.
        return cloned;
    }
}